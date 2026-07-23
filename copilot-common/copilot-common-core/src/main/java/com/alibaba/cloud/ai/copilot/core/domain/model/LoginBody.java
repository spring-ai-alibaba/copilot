package com.alibaba.cloud.ai.copilot.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录对象（精简版：仅用户名 + 密码）
 *
 * @author yzm
 */
@Data
public class LoginBody {

    /** 用户名 */
    @NotBlank(message = "{user.username.not.blank}")
    private String username;

    /** 用户密码 */
    @NotBlank(message = "{user.password.not.blank}")
    private String password;
}
