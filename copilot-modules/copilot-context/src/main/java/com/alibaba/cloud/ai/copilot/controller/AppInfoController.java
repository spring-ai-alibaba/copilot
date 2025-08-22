package com.alibaba.cloud.ai.copilot.controller;

import com.alibaba.cloud.ai.copilot.dto.AppInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * Application information controller
 */
@Slf4j
@RestController
@RequestMapping("/api/appInfo")
public class AppInfoController {

    /**
     * Get application information
     */
    @GetMapping
    public AppInfoResponse getAppInfo(@RequestParam(value = "language", defaultValue = "en") String language) {

        AppInfoResponse appInfo = new AppInfoResponse();
        appInfo.setName("alibaba copilot");
        appInfo.setVersion("1.0.0");
        appInfo.setLanguage(language);
        appInfo.setStatus("ready");

        if ("zh".equals(language)) {
            appInfo.setDescription("alibaba copilot 是一个强大的 AI 驱动的代码生成和设计转换工具");
            appInfo.setFeatures(Arrays.asList(
                "AI 代码生成",
                "设计稿转代码",
                "支持多种 AI 模型",
                "实时预览",
                "项目管理"
            ));
        } else {
            appInfo.setDescription("alibaba copilot is a powerful AI-driven code generation and design conversion tool");
            appInfo.setFeatures(Arrays.asList(
                "AI Code Generation",
                "Design to Code",
                "Multiple AI Models Support",
                "Real-time Preview",
                "Project Management"
            ));
        }

        return appInfo;
    }
}
