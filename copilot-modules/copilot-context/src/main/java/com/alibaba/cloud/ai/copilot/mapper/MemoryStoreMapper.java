package com.alibaba.cloud.ai.copilot.mapper;

import com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆存储 Mapper
 *
 * @author better
 */
@Mapper
public interface MemoryStoreMapper extends BaseMapper<MemoryStoreEntity> {

    /**
     * 根据命名空间和键查询
     *
     * @param namespace 命名空间（JSON字符串）
     * @param key       键
     * @return 记忆实体
     */
    MemoryStoreEntity selectByNamespaceAndKey(@Param("namespace") String namespace,
                                               @Param("key") String key);

    /**
     * 根据命名空间查询所有记录
     *
     * @param namespace 命名空间（JSON字符串）
     * @return 记忆实体列表
     */
    List<MemoryStoreEntity> selectByNamespace(@Param("namespace") String namespace);

    /**
     * 根据用户ID查询
     *
     * @param userId 用户ID
     * @return 记忆实体列表
     */
    List<MemoryStoreEntity> selectByUserId(@Param("userId") Long userId);

    /**
     * 搜索（基于 JSON 字段）
     * 注意：这是一个简化实现，实际生产环境可能需要更复杂的 JSON 查询
     *
     * @param namespace 命名空间（JSON字符串）
     * @param filter    搜索过滤器（JSON对象）
     * @return 记忆实体列表
     */
    List<MemoryStoreEntity> searchByFilter(@Param("namespace") String namespace,
                                           @Param("filter") Map<String, Object> filter);
}
