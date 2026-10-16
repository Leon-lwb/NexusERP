package com.nexus.ai.engineering.service;

import com.nexus.ai.engineering.domain.EngineeringTaskVo;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.engineering.EngineeringActor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 提交后通知真实所有者；外层事务回滚时不发布完成事件。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringTaskNotifier {
    public void completed(EngineeringActor owner, EngineeringTaskVo task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(owner, task);
                }
            });
        } else {
            publish(owner, task);
        }
    }

    private void publish(EngineeringActor owner, EngineeringTaskVo task) {
        try {
            AiSseMessageHelper.sendEngineeringTask(owner.userId(), task);
        } catch (RuntimeException unavailable) {
            log.warn("工程任务完成通知失败，任务 {} 可通过查询恢复", task.taskId());
        }
    }
}
