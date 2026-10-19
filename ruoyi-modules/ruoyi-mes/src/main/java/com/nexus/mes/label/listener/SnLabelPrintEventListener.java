package com.nexus.mes.label.listener;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.event.SnCreatedEvent;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 同步接收造码事件：只在原MES事务内保存不可变任务，业务提交及宕机恢复均不依赖内存回调。 */
@Component
@RequiredArgsConstructor
@DS("mes")
public class SnLabelPrintEventListener {
    private final MesLabelTemplateMapper templateMapper;
    private final ProSnMapper proSnMapper;
    private final LabelRenderService renderService;
    private final LabelPrintService printService;

    /** 不使用 AFTER_COMMIT 或异步监听；任务保存失败必须传播，使SN及任务一起回滚。 */
    @EventListener
    public void onSnCreated(SnCreatedEvent event) {
        var templates = templateMapper.selectList(Wrappers.<MesLabelTemplate>lambdaQuery()
            .eq(MesLabelTemplate::getLabelType, LabelRenderService.TYPE_SN).eq(MesLabelTemplate::getEnabled, "Y"));
        if (templates.isEmpty()) return;
        var sn = proSnMapper.selectById(event.getSnId());
        if (sn == null) throw new ServiceException("SN打印任务缺少同事务内的SN依据");
        var fields = renderService.snFields(sn);
        for (var template : templates) {
            MesLabelPrintLog record = printService.newLog(sn.getSnId(), fields.get("snCode"), template, MesLabelPrintLog.SOURCE_AUTO);
            printService.freezeFields(record, fields);
            String payload;
            try {
                payload = renderService.render(template, fields);
            } catch (Exception renderingFailure) {
                printService.recordFailure(record, renderingFailure.getMessage());
                continue;
            }
            printService.submit(record, payload, renderService.charsetOf(template));
        }
    }
}
