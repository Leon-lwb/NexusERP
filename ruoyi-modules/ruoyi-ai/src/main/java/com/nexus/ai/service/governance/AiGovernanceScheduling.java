package com.nexus.ai.service.governance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 治理显式启用后注册维护任务；默认不因新功能启动调度。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "nexus.ai.governance", name = "enabled", havingValue = "true")
public class AiGovernanceScheduling {}
