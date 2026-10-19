package com.nexus.mes.label.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelPrintTask;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.domain.vo.LabelPrintTaskVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * 打印提交门面：先保存不可变MES任务，业务事务不发送，手动提交只在任务提交后尝试发送一次。
 */
@RequiredArgsConstructor
@DS("mes")
@Service
public class LabelPrintService {
    private final LabelPrintTaskStore tasks;
    private final LabelPrintDispatcher dispatcher;

    /**
     * 捕获当前操作者、租户和模板修订，后续业务字段在渲染前冻结。
     */
    public MesLabelPrintLog newLog(
            Long snId, String snCode, MesLabelTemplate template, String printSource) {
        MesLabelPrintLog printLog =
                MesLabelPrintLog.of(
                        snId,
                        snCode,
                        template.getTemplateId(),
                        template.getTemplateCode(),
                        template.getPrinterHost(),
                        template.getPrinterPort(),
                        printSource);
        try {
            if (LoginHelper.isLogin()) {
                printLog.setOperatorId(LoginHelper.getUserId());
                printLog.setOperatorName(LoginHelper.getUsername());
            }
        } catch (Exception e) {
            // 无登录上下文(异步/设备通道/单测): 操作人留空, 不阻断打印
        }
        printLog.setTenantId(currentTenantId());
        LabelPrintSnapshot.template(printLog, template);
        return printLog;
    }

    /**
     * 自动提交只在原MES业务事务内保存任务，彻底移除内存执行器及 afterCommit 发送路径。
     */
    public void submit(MesLabelPrintLog record, String payload, Charset charset) {
        if (!MesLabelPrintLog.SOURCE_AUTO.equals(record.getPrintSource())) {
            throw new ServiceException("自动任务来源必须为AUTO");
        }
        LabelPrintSnapshot.payload(record, payload, charset);
        tasks.create(record, null, true);
    }

    /**
     * 兼容布尔返回：仅 SENT 返回true，UNKNOWN及待发状态不声称已发送或出纸。
     */
    public boolean send(MesLabelPrintLog record, String payload, Charset charset) {
        return send(record, payload, charset, null);
    }

    /**
     * 带稳定手动请求ID的提交；重复同一请求复用原任务，不能再次写网络。
     */
    public boolean send(
            MesLabelPrintLog record, String payload, Charset charset, String requestId) {
        return "SENT".equals(submitAndSend(record, payload, charset, requestId).getStatus());
    }

    /**
     * 正常手动调用先完成持久化事务再发送；外层业务事务存在时仅返回已保存待发任务。
     */
    public MesLabelPrintTask submitAndSend(
            MesLabelPrintLog record, String payload, Charset charset, String requestId) {
        LabelPrintSnapshot.payload(record, payload, charset);
        MesLabelPrintTask task =
                tasks.create(
                        record,
                        requestId,
                        MesLabelPrintLog.SOURCE_AUTO.equals(record.getPrintSource()));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return task;
        }
        return dispatcher.dispatch(task.getTaskId());
    }

    /**
     * 渲染失败也保存不可发送的终态任务及审计；保存失败必须让所属业务事务回滚。
     */
    public void recordFailure(MesLabelPrintLog record, String reason) {
        record.setResult("FAIL");
        record.setFailReason(
                reason == null ? null : reason.substring(0, Math.min(500, reason.length())));
        tasks.create(record, null, MesLabelPrintLog.SOURCE_AUTO.equals(record.getPrintSource()));
    }

    /**
     * 渲染前冻结字段，不保留调用方可变Map。
     */
    public void freezeFields(MesLabelPrintLog record, Map<String, String> fields) {
        LabelPrintSnapshot.fields(record, fields);
    }

    /**
     * 新任务和旧日志均按认证租户只读查询。
     */
    public MesLabelPrintLog findLog(Long id) {
        return tasks.findRecord(id);
    }

    /**
     * 兼容旧查询签名，不赋予无原因补打权限。
     */
    public MesLabelPrintLog findLatestLog(Long snId, Long templateId) {
        return tasks.findLatestRecord(snId, templateId);
    }

    /**
     * 当前租户任务状态，不将完整指令或布局直接暴露到状态接口。
     */
    public LabelPrintTaskVo taskInfo(Long taskId) {
        MesLabelPrintTask task = tasks.find(taskId);
        if (task == null) {
            throw new ServiceException("打印任务不存在或无权访问");
        }
        return view(task);
    }

    /**
     * 构造可供PC判断待发、未知及已发送的任务回执。
     */
    public LabelPrintTaskVo view(MesLabelPrintTask task) {
        LabelPrintTaskVo view = new LabelPrintTaskVo();
        view.setSnId(task.getSnId());
        view.setTemplateId(task.getTemplateId());
        view.setTemplateRevision(task.getTemplateRevision());
        view.setTaskId(task.getTaskId());
        view.setStatus(task.getStatus());
        view.setPrintSource(task.getPrintSource());
        view.setOriginalPrintLogId(task.getOriginalPrintLogId());
        view.setReprintReason(task.getReprintReason());
        view.setAttemptCount(task.getAttemptCount());
        view.setDetail(task.getLastError());
        return view;
    }

    /**
     * 补打复制原冻结证据，本次原因及原任务引用独立保存；不修改原任务或签署DHR。
     */
    public MesLabelPrintLog newReprintLog(
            MesLabelPrintLog original, String reason, boolean legacyRequest) {
        MesLabelPrintLog attempt =
                MesLabelPrintLog.of(
                        original.getSnId(),
                        original.getSnCode(),
                        original.getTemplateId(),
                        original.getTemplateCode(),
                        original.getPrinterHost(),
                        original.getPrinterPort(),
                        MesLabelPrintLog.SOURCE_REPRINT);
        attempt.setTenantId(currentTenantId());
        if (StringUtils.isBlank(attempt.getTenantId())) {
            attempt.setTenantId(original.getTenantId());
        }
        try {
            if (LoginHelper.isLogin()) {
                attempt.setOperatorId(LoginHelper.getUserId());
                attempt.setOperatorName(LoginHelper.getUsername());
            }
        } catch (Exception e) {
            // 设备或无登录上下文不补造操作人。
        }
        attempt.setTemplateRevision(original.getTemplateRevision());
        attempt.setTemplateSnapshot(original.getTemplateSnapshot());
        attempt.setFieldSnapshot(original.getFieldSnapshot());
        attempt.setContentHash(original.getContentHash());
        attempt.setPayloadHash(original.getPayloadHash());
        attempt.setRenderedPayload(original.getRenderedPayload());
        attempt.setPayloadCharset(original.getPayloadCharset());
        attempt.setOriginalPrintLogId(original.getLogId());
        attempt.setReprintReason(reason);
        attempt.setSnapshotMode(legacyRequest ? "LEGACY_REQUEST" : original.getSnapshotMode());
        if (StringUtils.isBlank(attempt.getSnapshotMode())) {
            attempt.setSnapshotMode("LEGACY_RECORD");
        }
        if (legacyRequest) {
            attempt.setRemark("历史接口补打：未提供补打原因；复用原任务冻结指令，未确认实际出纸");
        } else if (!"FROZEN".equals(attempt.getSnapshotMode())) {
            attempt.setRemark("原任务来自历史兼容记录；复用其已保存指令，原任务引用保留历史来源");
        }
        return attempt;
    }

    /**
     * 当前租户由认证上下文提供；持久化层会再次严格核验，缺失时不退回默认租户。
     */
    private static String currentTenantId() {
        try {
            return TenantHelper.getTenantId();
        } catch (Exception | Error unavailable) {
            return null;
        }
    }

    /**
     * 保留旧Java关闭签名；已没有可丢失任务的内存执行器。
     */
    public void shutdown() {}
}
