package com.alibaba.cloud.ai.example.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class CopilotApplication {

	private static final Logger logger = LoggerFactory.getLogger(CopilotApplication.class);
	private static final String PROJECT_DIR = "C:\\project\\spring-ai-alibaba-copilot";

	public static void main(String[] args) {
		// 在Spring Boot启动之前检查并创建目录
		checkAndCreateProjectDirectory();
		// 启动Spring Boot应用
		SpringApplication.run(CopilotApplication.class, args);
		logger.info("(♥◠‿◠)ﾉﾞ  AI Copilot启动成功   ლ(´ڡ`ლ)ﾞ");
	}

	/**
	 * 检查并创建项目目录
	 */
	private static void checkAndCreateProjectDirectory() {
		try {
			Path projectPath = Paths.get(PROJECT_DIR);

			if (!Files.exists(projectPath)) {
				logger.info("项目目录不存在，正在创建: {}", PROJECT_DIR);
				Files.createDirectories(projectPath);
				logger.info("成功创建项目目录: {}", PROJECT_DIR);
			} else {
				logger.info("项目目录已存在: {}", PROJECT_DIR);
			}
		} catch (Exception e) {
			logger.error("创建项目目录失败: {}", PROJECT_DIR, e);
			// 可以选择是否要因为目录创建失败而终止应用启动
			// System.exit(1); // 如果需要强制退出的话
			throw new RuntimeException("项目目录创建失败", e);
		}
	}
}
