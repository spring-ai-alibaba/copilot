package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
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
