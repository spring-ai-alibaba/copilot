package com.alibaba.cloud.ai.copilot.controller;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理控制器
 * 提供系统管理和监控接口
 *
 * @author Alibaba Cloud AI Team
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    /**
     * 获取应用信息
     */
    @GetMapping("/info")
    public Map<String, Object> getAppInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Spring AI Alibaba Copilot");
        info.put("description", "AI编程助手 - 模块化架构");
        info.put("timestamp", LocalDateTime.now());
        return info;
    }

}
