package com.alibaba.cloud.ai.copilot.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.cloud.ai.copilot.core.utils.SpringUtils;
import com.alibaba.cloud.ai.copilot.security.config.properties.SecurityProperties;
import com.alibaba.cloud.ai.copilot.security.handler.AllUrlHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * 权限安全配置
 *
 * @author yzm
 */

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;

    /**
     * 提供 AllUrlHandler Bean（用于收集所有可匹配的 URL）
     */
    @Bean
    public AllUrlHandler allUrlHandler() {
        return new AllUrlHandler();
    }

    /**
     * 注册sa-token的拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册路由拦截器，自定义验证规则
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 登录验证：对所有路径进行检查，拦截器本身已排除 excludes
            SaRouter
                .match("/**")
                .check(() -> {
                    // 检查是否登录 是否有token
                    StpUtil.checkLogin();
                });
        })).addPathPatterns("/**")
            // 排除不需要拦截的路径
            .excludePathPatterns(securityProperties.getExcludes());
    }

}
