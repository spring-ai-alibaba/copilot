package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.ChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 *
 * @author better
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}

