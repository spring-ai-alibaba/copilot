package com.alibaba.cloud.ai.copilot.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProviderEnum {
    QIANWEN("Tongyi-Qianwen"),
    DEEPSEEK("DeepSeek"),
    OPENAI("OpenAI"),
    SILICONFLOW("SILICONFLOW");
    
    private final String providerCode;
}
