package com.nexus.ai.service.governance;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** 新结构迁移后才显式启用；默认启动不访问第二阶段数据表。 */
@Data
@Component
@ConfigurationProperties(prefix = "nexus.ai.governance")
public class AiGovernanceProperties {
    private boolean enabled = false;

    /** 仅填入已核实输入计数、输出封顶和禁用隐藏重试的模型ID。默认无认证模型，严格预算拒绝。 */
    private Set<Long> budgetVerifiedModelIds = new HashSet<>();

    private boolean chatEnabled = false;
    private boolean flowEnabled = false;
    private boolean abEnabled = false;

    public boolean chatActive() {
        return enabled && chatEnabled;
    }

    public boolean flowActive() {
        return enabled && flowEnabled;
    }

    public boolean abActive() {
        return enabled && abEnabled;
    }
}
