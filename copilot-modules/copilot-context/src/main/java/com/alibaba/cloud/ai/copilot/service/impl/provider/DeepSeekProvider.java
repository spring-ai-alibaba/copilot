package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
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
