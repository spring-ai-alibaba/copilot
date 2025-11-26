package com.alibaba.cloud.ai.copilot.provider.impl;

import com.alibaba.cloud.ai.copilot.provider.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.provider.ProviderEnum;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * DeepSeek Provider 实现
 * 使用 OpenAI 兼容 API
 */
@Component
public class DeepSeekProvider extends AbstractOpenAiCompatibleProvider {

    public DeepSeekProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.DEEPSEEK.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return "https://api.deepseek.com";
    }

    @Override
    public boolean supportsFunctionCalling() {
        return true;
    }
}

