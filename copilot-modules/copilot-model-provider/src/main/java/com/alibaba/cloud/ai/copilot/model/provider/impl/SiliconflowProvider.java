package com.alibaba.cloud.ai.copilot.model.provider.impl;

import com.alibaba.cloud.ai.copilot.model.provider.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.model.provider.ProviderEnum;
import com.alibaba.cloud.ai.copilot.model.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * Siliconflow（硅基流动）Provider 实现
 * <p>
 * 使用 OpenAI 兼容 API
 * </p>
 */
@Component
public class SiliconflowProvider extends AbstractOpenAiCompatibleProvider {

    public SiliconflowProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.SILICONFLOW.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        return "https://api.siliconflow.cn";
    }
}
