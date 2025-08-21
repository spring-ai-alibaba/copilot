package com.alibaba.cloud.ai.copilot.service;

import com.alibaba.cloud.ai.copilot.model.PromptExtra;
import com.alibaba.cloud.ai.copilot.service.impl.BuilderHandlerImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统提示词构建测试
 * 测试不同文件类型的系统提示词生成
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class BuildSystemPromptTest {

    @Autowired
    private BuilderHandlerImpl builderHandler;

    @Test
    public void testBuildSystemPromptWithReact() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("react", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("REACT SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("functional components"));
        assertTrue(prompt.contains("hooks"));
        assertTrue(prompt.contains("JSX syntax"));
        
        log.info("React prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithVue() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("vue", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("VUE SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("Vue 3 Composition API"));
        assertTrue(prompt.contains("template syntax"));
        assertTrue(prompt.contains("reactive data"));
        
        log.info("Vue prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithAngular() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("angular", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("ANGULAR SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("Angular latest version"));
        assertTrue(prompt.contains("dependency injection"));
        assertTrue(prompt.contains("RxJS"));
        
        log.info("Angular prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithMiniProgram() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("miniprogram", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("MINI PROGRAM SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("WeChat Mini Program"));
        assertTrue(prompt.contains("WXML"));
        assertTrue(prompt.contains("WeUI"));
        
        log.info("MiniProgram prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithNodeJS() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("nodejs", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("NODE.JS SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("modern Node.js"));
        assertTrue(prompt.contains("async/await"));
        assertTrue(prompt.contains("npm packages"));
        
        log.info("NodeJS prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithPython() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("python", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("PYTHON SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("PEP 8"));
        assertTrue(prompt.contains("type hints"));
        assertTrue(prompt.contains("Python idioms"));
        
        log.info("Python prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithJava() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("java", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("JAVA SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("Java coding conventions"));
        assertTrue(prompt.contains("OOP principles"));
        assertTrue(prompt.contains("Java 8+"));
        
        log.info("Java prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithSpringBoot() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("springboot", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("SPRING BOOT SPECIFIC INSTRUCTIONS"));
        assertTrue(prompt.contains("Spring Boot conventions"));
        assertTrue(prompt.contains("dependency injection"));
        assertTrue(prompt.contains("REST API"));
        
        log.info("SpringBoot prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithUnknownType() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("unknown", null);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("GENERAL DEVELOPMENT INSTRUCTIONS"));
        assertTrue(prompt.contains("best practices"));
        assertTrue(prompt.contains("code structure"));
        
        log.info("Unknown type prompt length: {}", prompt.length());
    }

    @Test
    public void testBuildSystemPromptWithBackendConfig() throws Exception {
        PromptExtra config = new PromptExtra();
        config.setBackEnd(true);
        
        String prompt = callBuildSystemPromptWithFileType("java", config);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("backend code"));
        assertTrue(prompt.contains("CORS"));
        
        log.info("Backend config prompt length: {}", prompt.length());
    }

    @Test
    public void testPromptContainsBaseInstructions() throws Exception {
        String prompt = callBuildSystemPromptWithFileType("react", null);
        
        // 验证包含基础指令
        assertTrue(prompt.contains("We0 AI"));
        assertTrue(prompt.contains("boltArtifact"));
        assertTrue(prompt.contains("boltAction"));
        assertTrue(prompt.contains("CRITICAL EXAMPLE FORMAT"));
        
        log.info("Base instructions verified in React prompt");
    }

    @Test
    public void testCaseInsensitiveFileType() throws Exception {
        String promptLower = callBuildSystemPromptWithFileType("react", null);
        String promptUpper = callBuildSystemPromptWithFileType("REACT", null);
        String promptMixed = callBuildSystemPromptWithFileType("React", null);
        
        // 所有变体应该产生相同的结果
        assertEquals(promptLower, promptUpper);
        assertEquals(promptLower, promptMixed);
        
        log.info("Case insensitive test passed");
    }

    /**
     * 使用反射调用私有方法进行测试
     */
    private String callBuildSystemPromptWithFileType(String fileType, PromptExtra config) throws Exception {
        Method method = BuilderHandlerImpl.class.getDeclaredMethod(
            "buildSystemPromptWithFileType", String.class, PromptExtra.class);
        method.setAccessible(true);
        return (String) method.invoke(builderHandler, fileType, config);
    }
}
