package com.alibaba.cloud.ai.copilot.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 长期记忆存储实体
 *
 * @author better
 */
@Data
@TableName(value = "chat_memory_store", autoResultMap = true)
public class MemoryStoreEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 命名空间（JSON数组格式，如：["users","user_123"]）
     */
    private String namespace;

    /**
     * 记忆键
     */
    @TableField(value = "`key`")
    private String key;

    /**
     * 记忆值（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> value;

    /**
     * 用户ID（用于权限控制）
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
