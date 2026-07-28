package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.domain.entity.ModelConfigEntity;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Anthropic Messages API Provider。
 *
 * <p>支持 Anthropic 官方端点，也支持实现了 {@code /v1/messages} 的中转站。
 * Provider 决定协议，{@link ModelConfigEntity#getApiUrl()} 只决定请求发送到哪个站点。</p>
 */
@Slf4j
@Component
public class AnthropicProvider extends AbstractOpenAiCompatibleProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    /**
     * Coding agents often place the generated file content inside a tool-call JSON argument.
     * A 4K output budget can truncate that JSON before it is closed, causing the tool parser to
     * receive an empty argument map. Keep enough room for a complete, moderately sized tool call.
     */
    static final int MIN_TOOL_CALL_MAX_TOKENS = 16_384;

    public AnthropicProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.ANTHROPIC.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public Model createChatModel(ModelConfigEntity config) {
        return createChatModel(config, null);
    }

    @Override
    public Model createChatModel(ModelConfigEntity config, GenerateOptions options) {
        validateConfig(config);
        GenerateOptions defaultOptions = options != null ? options : createDefaultChatOptions(config);
        String baseUrl = resolveBaseUrl(config);

        log.debug("创建 Anthropic Model: model={}, baseUrl={}", config.getModelKey(), baseUrl);

        return AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelKey())
                .baseUrl(baseUrl)
                .stream(true)
                .defaultOptions(defaultOptions)
                .build();
    }

    @Override
    public GenerateOptions createChatOptions(
            ModelConfigEntity config, Integer maxTokens, Double temperature) {
        int effectiveMaxTokens = maxTokens != null
                ? maxTokens
                : Math.max(getDefaultMaxTokens(config), MIN_TOOL_CALL_MAX_TOKENS);
        return GenerateOptions.builder()
                .modelName(config.getModelKey())
                .maxTokens(effectiveMaxTokens)
                .temperature(temperature != null ? temperature : DEFAULT_TEMPERATURE)
                .topP(DEFAULT_TOP_P)
                .build();
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
    }

    @Override
    public boolean supportsMultimodal() {
        return true;
    }
}
