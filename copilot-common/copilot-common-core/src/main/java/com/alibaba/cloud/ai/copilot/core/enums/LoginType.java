package com.alibaba.cloud.ai.copilot.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录类型（精简版：仅保留密码登录）
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum LoginType {

    /** 密码登录 */
    PASSWORD("user.password.retry.limit.exceed", "user.password.retry.limit.count");

    /** 登录重试超出限制提示 */
    final String retryLimitExceed;

    /** 登录重试限制计数提示 */
    final String retryLimitCount;
}
