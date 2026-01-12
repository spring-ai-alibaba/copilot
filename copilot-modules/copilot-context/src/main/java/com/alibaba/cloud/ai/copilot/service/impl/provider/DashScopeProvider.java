package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
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
