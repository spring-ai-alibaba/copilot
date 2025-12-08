package com.alibaba.cloud.ai.copilot.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProviderEnum {
    ALIBAILIAN("ALiBaiLian"),
    DEEPSEEK("DeepSeek"),
    OPENAI("OpenAI"),
    SILICONFLOW("SILICONFLOW"),
    OPENAI_COMPATIBLE("OpenAiCompatible");
    
    private final String providerCode;
}
