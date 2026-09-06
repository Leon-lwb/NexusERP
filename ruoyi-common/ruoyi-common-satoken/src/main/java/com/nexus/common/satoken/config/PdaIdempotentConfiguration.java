package com.nexus.common.satoken.config;

import com.nexus.common.satoken.pda.PdaIdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * PDA 幂等切面装配(N4: 原 ERP/MES 双套 PdaIdempotentAspect 合并下沉)
 */
@AutoConfiguration
public class PdaIdempotentConfiguration {

    @Bean
    public PdaIdempotentAspect pdaIdempotentAspect() {
        return new PdaIdempotentAspect();
    }
}
