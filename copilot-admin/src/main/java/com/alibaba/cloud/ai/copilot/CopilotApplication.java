package com.alibaba.cloud.ai.copilot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Spring AI Alibaba Copilot 主启动类
 *
 * @author Alibaba Cloud AI Team
 */
@SpringBootApplication
@Slf4j
public class CopilotApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(CopilotApplication.class, args);
        log.info("(♥◠‿◠)ﾉﾞ  Alibaba Copilot启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
