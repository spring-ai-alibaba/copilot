package com.alibaba.cloud.ai.copilot.core.domain.model;

import lombok.Data;

import java.io.Serial;

/**
 * 游客登录用户身份权限
 */
@Data
public class VisitorLoginBody {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;

    /**
     * 登录类型(1.小程序访客 2.pc访客)
     */
    private String type;

}
