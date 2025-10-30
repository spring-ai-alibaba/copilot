package com.alibaba.cloud.ai.copilot.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PhoneLoginBody {
    @NotBlank(message = "手机号不能为空")
    private String phoneNumber;

    @NotBlank(message = "验证码不能为空")
    private String code;

    private String tenantId;

    private String loginType;


} 