package com.alibaba.cloud.ai.copilot.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.secure.BCrypt;
import com.alibaba.cloud.ai.copilot.core.domain.R;
import com.alibaba.cloud.ai.copilot.core.domain.model.LoginBody;
import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.core.exception.user.UserException;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.system.domain.bo.SysUserBo;
import com.alibaba.cloud.ai.copilot.system.domain.vo.LoginVo;
import com.alibaba.cloud.ai.copilot.system.service.ISysUserService;
import com.alibaba.cloud.ai.copilot.system.service.SysLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证
 *
 * @author yzm
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysLoginService loginService;
    private final ISysUserService userService;

    /** 登录 */
    @PostMapping("/login")
    @SaIgnore
    public R<LoginVo> login(@Validated @RequestBody LoginBody body) {
        LoginVo loginVo = new LoginVo();
        loginVo.setToken(loginService.login(body.getUsername(), body.getPassword()));
        loginVo.setUserInfo(LoginHelper.getLoginUser());
        return R.ok(loginVo);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout() {
        loginService.logout();
        return R.ok("退出成功");
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public R<LoginUser> me() {
        return R.ok(LoginHelper.getLoginUser());
    }

    /** 用户注册（极简：仅用户名 + 密码，无验证码/邮箱校验） */
    @PostMapping("/register")
    @SaIgnore
    public R<Void> register(@Validated @RequestBody LoginBody body) {
        String username = body.getUsername();
        SysUserBo bo = new SysUserBo();
        bo.setUserName(username);
        bo.setNickName(username);
        bo.setPassword(BCrypt.hashpw(body.getPassword()));
        bo.setUserBalance(1.0);
        if (!userService.checkUserNameUnique(bo)) {
            throw new UserException("user.username.registered", username);
        }
        if (userService.registerUser(bo) == null) {
            throw new UserException("user.register.failed");
        }
        return R.ok();
    }
}
