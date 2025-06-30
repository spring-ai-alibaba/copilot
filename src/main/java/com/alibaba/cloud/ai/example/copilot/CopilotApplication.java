package com.alibaba.cloud.ai.example.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CopilotApplication {

	private static final Logger logger = LoggerFactory.getLogger(CopilotApplication.class);

	public static void main(String[] args) {
		// 启动Spring Boot应用
		SpringApplication.run(CopilotApplication.class, args);
		logger.info("(♥◠‿◠)ﾉﾞ  AI Copilot启动成功   ლ(´ڡ`ლ)ﾞ");
	}

}
