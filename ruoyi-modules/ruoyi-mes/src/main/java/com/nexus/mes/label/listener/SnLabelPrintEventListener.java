package com.nexus.mes.label.listener;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelPrintLogMapper;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * SN 标签打印事件监听器
 * <p>监听 {@link SnCreatedEvent}(造码即发, M1 标签打印): 按启用中的 SN 模板渲染并
 * TCP 9100 直发网络打印机。</p>
 * <p>AFTER_COMMIT + 自管异步线程池: 打印是造码的旁路动作, 打印失败不回滚也不阻断造码;
 * fallbackExecution=true 兜底无事务发布场景(直接执行)。
 * 字段值在监听线程(登录/租户上下文)取齐后再提交线程池, 异步侧仅做 TCP 发送。</p>
 * <p><b>MES-LABEL-02 幂等</b>: 同一 (sn_id, template_id) 已存在成功打印留痕时跳过 —— 事件重放/重复造码
 * 不会重复出纸; 重打走显式补打入口({@code MesLabelTemplateController#reprint}, print_source=REPRINT)。
 * 多模板场景每个模板各一条留痕, 可追溯"一个 SN 打了几张"。</p>
 * <p><b>MES-LABEL-03 留痕</b>: 渲染失败不再静默 warn, 而是落 mes_label_print_log(result=FAIL + 原因)。</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DS("mes")
public class SnLabelPrintEventListener {

    private final MesLabelTemplateMapper templateMapper;
    private final MesLabelPrintLogMapper printLogMapper;
    private final ProSnMapper proSnMapper;
    private final LabelRenderService renderService;
    private final LabelPrintService printService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSnCreated(SnCreatedEvent event) {
        try {
            List<MesLabelTemplate> templates = templateMapper.selectList(Wrappers.<MesLabelTemplate>lambdaQuery()
                .eq(MesLabelTemplate::getLabelType, LabelRenderService.TYPE_SN)
                .eq(MesLabelTemplate::getEnabled, "Y"));
            if (templates.isEmpty()) {
                log.debug("SN 标签打印跳过: 无启用中的 SN 模板, snCode={}", event.getSnCode());
                return;
            }
            ProSn sn = proSnMapper.selectById(event.getSnId());
            if (sn == null) {
                log.warn("SN 标签打印跳过: SN 不存在, snId={}, snCode={}", event.getSnId(), event.getSnCode());
                return;
            }
            Map<String, String> fields = renderService.snFields(sn);
            for (MesLabelTemplate template : templates) {
                // MES-LABEL-02: 幂等键 (sn_id, template_id) —— 已有成功记录则跳过, 防事件重放/重复打印
                if (alreadyPrinted(event.getSnId(), template.getTemplateId())) {
                    log.info("SN 标签打印跳过(该SN该模板已成功打印, 幂等): snId={}, template={}",
                        event.getSnId(), template.getTemplateCode());
                    continue;
                }
                MesLabelPrintLog printLog = printService.newLog(sn.getSnId(), sn.getSnCode(), template,
                    MesLabelPrintLog.SOURCE_AUTO);
                try {
                    String payload = renderService.render(template, fields);
                    printService.submit(printLog, payload, renderService.charsetOf(template));
                } catch (Exception e) {
                    // MES-LABEL-03: 渲染失败不再静默 —— 落 FAIL 留痕(含原因), 便于发现脏模板
                    log.warn("SN 标签渲染失败(已落 FAIL 留痕): template={}, snCode={}, error={}",
                        template.getTemplateCode(), sn.getSnCode(), e.getMessage());
                    printService.recordFailure(printLog, e.getMessage());
                }
            }
        } catch (Exception e) {
            // 旁路动作: 任何异常不外抛(不影响造码事务后的调用方)
            log.warn("SN 标签打印联动失败(不影响造码): snCode={}, error={}", event.getSnCode(), e.getMessage());
        }
    }

    /**
     * MES-LABEL-02: 幂等判断 —— 同一 (sn_id, template_id) 是否已有成功打印记录
     * <p>只认 SUCCESS: FAIL 留痕不阻止后续重试(打印机离线恢复后仍应出纸)。</p>
     */
    private boolean alreadyPrinted(Long snId, Long templateId) {
        if (snId == null || templateId == null) {
            return false;
        }
        Long count = printLogMapper.selectCount(Wrappers.<MesLabelPrintLog>lambdaQuery()
            .eq(MesLabelPrintLog::getSnId, snId)
            .eq(MesLabelPrintLog::getTemplateId, templateId)
            .eq(MesLabelPrintLog::getResult, MesLabelPrintLog.RESULT_SUCCESS));
        return count != null && count > 0;
    }
}
