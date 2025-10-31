package com.alibaba.cloud.ai.copilot.system.domain.vo;

import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import lombok.Data;

/**
 * 登录返回信息
 *
 * @author Michelle.Chung
 */
@Data
public class LoginVo {
    private String token;
    private LoginUser userInfo;
}
