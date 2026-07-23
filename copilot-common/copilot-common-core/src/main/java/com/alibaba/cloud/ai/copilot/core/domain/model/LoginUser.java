package com.alibaba.cloud.ai.copilot.core.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户身份权限（精简版：仅保留认证链路实际用到的字段）
 *
 * @author yzm
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户类型（sys_user / app_user） */
    private String userType;

    /** 用户名 */
    private String username;

    /** 头像 */
    private String avatar;

    /** 获取登录id：userType:userId */
    public String getLoginId() {
        if (userType == null) {
            throw new IllegalArgumentException("用户类型不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userType + ":" + userId;
    }
}
