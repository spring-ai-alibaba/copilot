package com.alibaba.cloud.ai.copilot.core.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户注册对象
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterBody extends LoginBody {

    private String userType;

    /**
     * 注册域名
     */
    private String domainName;

}
