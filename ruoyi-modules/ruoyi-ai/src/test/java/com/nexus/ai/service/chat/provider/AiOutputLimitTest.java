package com.nexus.ai.service.chat.provider;

import static org.assertj.core.api.Assertions.*;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;

import org.junit.jupiter.api.Test;

import java.util.List;

class AiOutputLimitTest {
    @Test
    void nativeProvidersHonorApplicationLimitAndModelDefault() {
        var config = new AiModel();
        config.setModelName("fixture");
        config.setApiKey("fixture-key");
        config.setMaxTokens(73);
        var request = new AiChatRequest();
        request.setMaxOutputTokens(11);
        for (ChatServiceProvider p :
                List.of(
                        new DashScopeServiceProvider(List.of()),
                        new ZhiPuServiceProvider(List.of()))) {
            assertThat(
                            p.buildStreamingChatModel(config, request)
                                    .defaultRequestParameters()
                                    .maxOutputTokens())
                    .as(p.getProtocol())
                    .isEqualTo(11);
            assertThat(p.buildChatModel(config, 0.1).defaultRequestParameters().maxOutputTokens())
                    .as(p.getProtocol())
                    .isEqualTo(73);
        }
    }
}
