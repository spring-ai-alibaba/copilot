package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
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
