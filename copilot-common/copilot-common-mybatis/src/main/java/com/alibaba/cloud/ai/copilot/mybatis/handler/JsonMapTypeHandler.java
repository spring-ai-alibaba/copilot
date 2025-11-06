package com.alibaba.cloud.ai.copilot.mybatis.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 Map<String, Object> 类型处理器
 * 专门处理 JSON 字段到 Map<String, Object> 的转换
 */
@MappedTypes({Map.class})
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(JsonMapTypeHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<Map<String, Object>>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            throw new SQLException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            Map<String, Object> result = objectMapper.readValue(json, TYPE_REFERENCE);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            log.error("JSON反序列化失败, json: {}", json, e);
            // 返回空Map而不是抛出异常，避免查询失败
            return new HashMap<>();
        }
    }
}