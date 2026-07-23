package com.alibaba.cloud.ai.copilot.core.enums;

import com.alibaba.cloud.ai.copilot.core.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型（多套用户体系；精简版仅保留系统用户）
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum UserType {

    /** 系统用户 */
    SYS_USER("sys_user");

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
