package com.nexus.ai.service.governance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 默认关闭；已迁移并启用台账后才维护待核对调用。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiGovernanceRecovery {
    private final AiCallLedgerService ledger;
    private final AiGovernanceProperties properties;

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        if (properties.isEnabled())
            try {
                ledger.recoverUnconfirmed();
            } catch (RuntimeException error) {
                log.warn("AI调用台账维护暂未完成，将在下轮重试");
            }
    }
}
