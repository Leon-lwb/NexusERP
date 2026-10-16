package com.nexus.ai.engineering.task;

import com.nexus.ai.engineering.service.EngineeringTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界工程维护扫描；多实例竞争依靠任务修订 CAS，不自动重做编辑器写入。 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringTaskMaintenance {
    private final EngineeringTaskService tasks;
    private final com.nexus.ai.engineering.service.EngineeringArtifactUploadService uploads;

    /** 每三十秒终结超期任务，关闭功能时不会装配或访问工程表。 */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void scan() {
        try {
            tasks.maintain();
            uploads.cleanupUnconfirmed();
        } catch (Exception e) {
            log.warn("工程任务维护本轮失败，将在下轮重试");
        }
    }
}
