package com.alibaba.cloud.ai.copilot.service.impl.provider;

import com.alibaba.cloud.ai.copilot.service.AbstractOpenAiCompatibleProvider;
import com.alibaba.cloud.ai.copilot.service.LlmService;
import com.alibaba.cloud.ai.copilot.enums.ProviderEnum;
import org.springframework.stereotype.Component;

/**
 * OpenAI Compatible Provider 实现
 * 支持任何兼容 OpenAI API 的供应商
 * 用户可以自定义 URL、API Key 和模型名称
 */
@Component
public class OpenAiCompatibleProvider extends AbstractOpenAiCompatibleProvider {

    public OpenAiCompatibleProvider(LlmService llmService) {
        super(llmService);
    }

    @Override
    public String getProviderName() {
        return ProviderEnum.OPENAI_COMPATIBLE.getProviderCode();
    }

    @Override
    public String getDefaultBaseUrl() {
        // OpenAI Compatible 没有默认 URL，必须由用户提供
        return null;
    }

    @Override
    public boolean supportsFunctionCalling() {
        // 大部分兼容 OpenAI 的供应商都支持函数调用
        return true;
    }
}
