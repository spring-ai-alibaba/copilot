package com.alibaba.cloud.ai.copilot.model.provider.impl;

import com.alibaba.cloud.ai.copilot.model.provider.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.model.provider.ProviderEnum;
import com.alibaba.cloud.ai.copilot.model.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * DashScope（通义千问）Provider 实现
 * 使用 OpenAI 兼容 API
 */
@Component
public class DashScopeProvider extends AbstractOpenAiCompatibleProvider {

    public DashScopeProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.ALIBAILIAN.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return "https://dashscope.aliyuncs.com/compatible-mode";
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
