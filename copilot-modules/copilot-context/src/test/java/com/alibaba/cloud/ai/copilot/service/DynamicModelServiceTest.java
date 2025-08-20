package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.ApiConfig;
import com.alibaba.cloud.ai.copilot.service.impl.DynamicModelServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 动态模型服务测试类
 */
@ExtendWith(MockitoExtension.class)
class DynamicModelServiceTest {

    @Mock
    private ApiConfigService apiConfigService;

    private DynamicModelService dynamicModelService;

    @BeforeEach
    void setUp() {
        dynamicModelService = new DynamicModelServiceImpl(apiConfigService);
    }

    @Test
    void testGetChatModel_WithValidConfig() {
        // 准备测试数据
        ApiConfig config = new ApiConfig();
        config.setModelName("gpt-3.5-turbo");
        config.setApiKey("sk-test-api-key");
        config.setBaseUrl("https://api.openai.com");
        config.setStatus(1);

        // 模拟服务调用
        when(apiConfigService.getApiConfig("gpt-3.5-turbo", "user123"))
                .thenReturn(config);

        // 执行测试
        ChatModel chatModel = dynamicModelService.getChatModel("gpt-3.5-turbo", "user123");

        // 验证结果
        assertNotNull(chatModel);
    }

    @Test
    void testGetChatModel_WithNullConfig() {
        // 模拟服务调用返回null
        when(apiConfigService.getApiConfig(anyString(), anyString()))
                .thenReturn(null);

        // 执行测试并验证异常
        assertThrows(IllegalArgumentException.class, () -> {
            dynamicModelService.getChatModel("gpt-3.5-turbo", "user123");
        });
    }

    @Test
    void testGetChatModel_WithEmptyApiKey() {
        // 准备测试数据 - API Key为空
        ApiConfig config = new ApiConfig();
        config.setModelName("gpt-3.5-turbo");
        config.setApiKey("");
        config.setStatus(1);

        // 模拟服务调用
        when(apiConfigService.getApiConfig("gpt-3.5-turbo", "user123"))
                .thenReturn(config);

        // 执行测试并验证异常
        assertThrows(IllegalArgumentException.class, () -> {
            dynamicModelService.getChatModel("gpt-3.5-turbo", "user123");
        });
    }

    @Test
    void testIsModelAvailable_WithValidConfig() {
        // 准备测试数据
        ApiConfig config = new ApiConfig();
        config.setModelName("gpt-3.5-turbo");
        config.setApiKey("sk-test-api-key");
        config.setStatus(1);

        // 模拟服务调用
        when(apiConfigService.getApiConfig("gpt-3.5-turbo", "user123"))
                .thenReturn(config);

        // 执行测试
        boolean available = dynamicModelService.isModelAvailable("gpt-3.5-turbo", "user123");

        // 验证结果
        assertTrue(available);
    }

    @Test
    void testIsModelAvailable_WithInvalidConfig() {
        // 模拟服务调用返回null
        when(apiConfigService.getApiConfig(anyString(), anyString()))
                .thenReturn(null);

        // 执行测试
        boolean available = dynamicModelService.isModelAvailable("gpt-3.5-turbo", "user123");

        // 验证结果
        assertFalse(available);
    }

    @Test
    void testRefreshModelCache() {
        // 执行测试 - 应该不抛出异常
        assertDoesNotThrow(() -> {
            dynamicModelService.refreshModelCache();
        });
    }
}
