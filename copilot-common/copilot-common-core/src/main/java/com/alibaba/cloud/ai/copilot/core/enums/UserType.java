package com.alibaba.cloud.ai.copilot.core.enums;

import com.alibaba.cloud.ai.copilot.core.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备类型
 * 针对多套 用户体系
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum UserType {

    /**
     * pc端
     */
    SYS_USER("sys_user"),

    /**
     * app端
     */
    APP_USER("app_user");

    private final String userType;

    public static UserType getUserType(String str) {
        for (UserType value : values()) {
            if (StringUtils.contains(str, value.getUserType())) {
                return value;
            }
        }
        throw new RuntimeException("'UserType' not found By " + str);
    }
}
