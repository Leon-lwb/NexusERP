package com.nexus.common.flowable.helper;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 流程实例 ID 三级降级查询助手。
 * <p>
 * 收敛全仓 15 处 {@code getProcInsId} 的三级查询样板：
 * <ol>
 *   <li>优先通过业务桥接表查询（调用方以 {@code Supplier<String>} 注入，异常记 warn 降级）</li>
 *   <li>运行时流程实例查询（{@link RuntimeService}）</li>
 *   <li>历史流程实例查询（{@link HistoryService}）</li>
 * </ol>
 * 未找到返回 {@code null}。
 * </p>
 *
 * @author generated
 * @since 2026-08-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowableProcInsIdHelper {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    /**
     * 三级降级查询流程实例 ID。
     *
     * @param businessKey    业务数据 ID（字符串形式，对应 Flowable businessKey）
     * @param businessLookup 第一级：业务桥接表查询，返回流程实例 ID 或 {@code null}
     * @return 流程实例 ID，未找到返回 {@code null}
     */
    public String resolve(String businessKey, Supplier<String> businessLookup) {
        // 第一级：查业务桥接表（wf_my_business）
        try {
            String procInsId = businessLookup.get();
            if (StrUtil.isNotBlank(procInsId)) {
                return procInsId;
            }
        } catch (Exception e) {
            log.warn("Flowable 业务桥接表查询失败, businessKey={}", businessKey, e);
        }

        // 第二级：查运行时流程实例
        ProcessInstance runtime = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(businessKey)
            .singleResult();
        if (runtime != null) {
            return runtime.getId();
        }

        // 第三级：查历史流程实例
        HistoricProcessInstance hist = historyService.createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(businessKey)
            .singleResult();
        return hist != null ? hist.getId() : null;
    }
}