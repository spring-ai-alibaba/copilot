package com.alibaba.cloud.ai.copilot.provider.impl;

import com.alibaba.cloud.ai.copilot.provider.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.provider.ProviderEnum;
import com.alibaba.cloud.ai.copilot.service.LlmService;
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
        return ProviderEnum.QIANWEN.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return "https://dashscope.aliyuncs.com/compatible-mode/v1";
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

