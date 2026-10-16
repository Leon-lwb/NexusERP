package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.engineering.config.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.ai.tool.EasyedaEngineeringToolProvider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EngineeringDisabledConfigurationTest {
    @Test
    void defaultDisabledContextStartsWithoutDatabaseRedisIdentityOrStorage() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        EngineeringProperties.class,
                        EngineeringConfiguration.class,
                        EngineeringRepository.class,
                        EngineeringDeviceService.class,
                        EngineeringAgentFactory.class,
                        EasyedaEngineeringToolProvider.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertFalse(context.getBean(EngineeringProperties.class).isEnabled());
                            assertEquals(
                                    0,
                                    context.getBeanNamesForType(EngineeringRepository.class)
                                            .length);
                            assertEquals(
                                    0,
                                    context.getBeanNamesForType(EngineeringDeviceService.class)
                                            .length);
                            assertEquals(
                                    0,
                                    context.getBeanNamesForType(EngineeringAgentFactory.class)
                                            .length);
                        });
    }
}
