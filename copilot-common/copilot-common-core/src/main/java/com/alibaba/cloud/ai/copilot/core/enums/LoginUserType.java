package com.alibaba.cloud.ai.copilot.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游客登录类型
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum LoginUserType {

    PC("1", "PC端用户"),

    XCX("2", "小程序用户");

    private final String code;
    private final String content;
}
