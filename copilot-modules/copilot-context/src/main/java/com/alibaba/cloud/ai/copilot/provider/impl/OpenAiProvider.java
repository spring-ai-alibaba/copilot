package com.alibaba.cloud.ai.copilot.provider.impl;

import com.alibaba.cloud.ai.copilot.provider.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.provider.ProviderEnum;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * OpenAI Provider 实现
 */
@Component
public class OpenAiProvider extends AbstractOpenAiCompatibleProvider {

    public OpenAiProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.OPENAI.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return "https://api.openai.com/v1";
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
