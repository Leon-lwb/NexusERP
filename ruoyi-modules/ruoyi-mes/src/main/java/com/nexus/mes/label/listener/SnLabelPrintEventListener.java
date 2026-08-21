package com.nexus.mes.label.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.mes.label.domain.MesLabelTemplate;
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SN 标签打印事件监听器
 * <p>监听 {@link SnCreatedEvent}(造码即发, M1 标签打印): 按启用中的 SN 模板渲染并
 * TCP 9100 直发网络打印机。</p>
 * <p>AFTER_COMMIT + 自管异步线程池: 打印是造码的旁路动作, 打印失败不回滚也不阻断造码;
 * fallbackExecution=true 兜底无事务发布场景(直接执行)。
 * 字段值在监听线程(登录/租户上下文)取齐后再提交线程池, 异步侧仅做 TCP 发送。</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnLabelPrintEventListener {

    private final MesLabelTemplateMapper templateMapper;
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
            Map<String, String> fields = buildFields(sn);
            for (MesLabelTemplate template : templates) {
                String payload = renderService.render(template, fields);
                printService.submit(template.getTemplateCode(), template.getPrinterHost(),
                    template.getPrinterPort() == null ? 9100 : template.getPrinterPort(),
                    payload, renderService.charsetOf(template));
            }
        } catch (Exception e) {
            // 旁路动作: 任何异常不外抛(不影响造码事务后的调用方)
            log.warn("SN 标签打印联动失败(不影响造码): snCode={}, error={}", event.getSnCode(), e.getMessage());
        }
    }

    /** SN 六字段值域(与模板字段绑定一一对应) */
    private Map<String, String> buildFields(ProSn sn) {
        Map<String, String> fields = new HashMap<>(8);
        fields.put("snCode", sn.getSnCode());
        fields.put("materialCode", sn.getMaterialCode());
        fields.put("materialName", sn.getMaterialName());
        fields.put("woNo", sn.getWoNo());
        fields.put("cardNo", sn.getCardNo());
        fields.put("productionDate", formatDate(sn.getProductionDate()));
        return fields;
    }

    private String formatDate(Date date) {
        return date == null ? "" : String.format("%tF", date);
    }
}
