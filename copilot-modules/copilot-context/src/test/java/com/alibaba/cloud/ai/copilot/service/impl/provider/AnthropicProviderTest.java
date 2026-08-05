package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AnthropicProviderTest {

    @Test
    void createsNativeAnthropicModelForCustomRelay() {
        AnthropicProvider provider = new AnthropicProvider(null);
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider(ProviderEnum.ANTHROPIC.getProviderCode());
        config.setModelKey("claude-sonnet-4-6");
        config.setApiKey("test-key");
        config.setApiUrl("https://relay.example/api");
        config.setMaxToken(4096);

        Model model = provider.createChatModel(config);

        AnthropicChatModel anthropicModel = assertInstanceOf(AnthropicChatModel.class, model);
        assertEquals("claude-sonnet-4-6", anthropicModel.getModelName());
        assertEquals("Anthropic", provider.getProviderName());
        assertEquals("https://api.anthropic.com", provider.getDefaultBaseUrl());
    }

    @Test
    void raisesConfiguredDefaultToSafeToolCallBudget() {
        AnthropicProvider provider = new AnthropicProvider(null);
        ModelConfigEntity config = createConfig(4096);

        GenerateOptions options = provider.createChatOptions(config, null, null);

        assertEquals(AnthropicProvider.MIN_TOOL_CALL_MAX_TOKENS, options.getMaxTokens());
    }

    @Test
    void preservesLargerConfiguredAndExplicitBudgets() {
        AnthropicProvider provider = new AnthropicProvider(null);
        ModelConfigEntity config = createConfig(32_768);

        assertEquals(32_768, provider.createChatOptions(config, null, null).getMaxTokens());
        assertEquals(2_048, provider.createChatOptions(config, 2_048, null).getMaxTokens());
    }

    private ModelConfigEntity createConfig(int maxTokens) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProvider(ProviderEnum.ANTHROPIC.getProviderCode());
        config.setModelKey("claude-sonnet-4-6");
        config.setApiKey("test-key");
        config.setApiUrl("https://relay.example/api");
        config.setMaxToken(maxTokens);
        return config;
    }
}
