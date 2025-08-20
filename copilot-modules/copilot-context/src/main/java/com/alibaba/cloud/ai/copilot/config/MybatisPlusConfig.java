package com.alibaba.cloud.ai.copilot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus配置类
 */
@Configuration
@MapperScan("com.alibaba.cloud.ai.copilot.mapper")
public class MybatisPlusConfig {

    /**
     * 分页插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }

    /**
     * 自动填充处理器
     */
    @Component
    public static class MyMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, LocalDateTime.now());
            this.strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
        }
    }
}
