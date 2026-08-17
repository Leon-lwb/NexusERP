package com.nexus.mes.flash.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MES 刷写配置 (前缀 nexus.mes.flash)
 * <p>工序打标关键字: generateStepsFromRouting 按 process_code 匹配关键字落 step_type。
 * 未匹配则 GENERAL(不阻塞)。</p>
 *
 * @author Nexus
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "nexus.mes.flash")
public class FwProperties {

    /** 刷写工序关键字(process_code 含其一则 step_type=FLASH, 大小写不敏感) */
    private List<String> flashKeywords = List.of("FLASH", "BURN");

    /** 测试工序关键字(process_code 含其一则 step_type=TEST, 大小写不敏感) */
    private List<String> testKeywords = List.of("FCT", "TEST", "FUNC");
}
