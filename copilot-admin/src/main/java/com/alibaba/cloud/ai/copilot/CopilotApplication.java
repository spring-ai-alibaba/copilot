package com.alibaba.cloud.ai.copilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring AI Alibaba Copilot 主启动类
 *
 * @author Alibaba Cloud AI Team
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.alibaba.cloud.ai.copilot.**.mapper")
public class CopilotApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(CopilotApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  Alibaba Copilot启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
