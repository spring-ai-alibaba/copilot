package com.alibaba.cloud.ai.copilot.satoken.utils;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.core.enums.DeviceType;
import com.alibaba.cloud.ai.copilot.core.enums.UserType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录鉴权助手（精简版）
 * <p>
 * 仅保留认证链路实际使用的方法：登录、读取当前用户、读取 userId/userType。
 * 会话存储由 Sa-Token 默认内存 DAO 承载（不再依赖 Redis）。
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginHelper {

    public static final String LOGIN_USER_KEY = "loginUser";
    public static final String USER_KEY = "userId";

    /** 登录系统 */
    public static void login(LoginUser loginUser) {
        loginByDevice(loginUser, null);
    }

    /** 登录系统（基于设备类型） */
    public static void loginByDevice(LoginUser loginUser, DeviceType deviceType) {
        SaStorage storage = SaHolder.getStorage();
        storage.set(LOGIN_USER_KEY, loginUser);
        storage.set(USER_KEY, loginUser.getUserId());
        SaLoginModel model = new SaLoginModel();
        if (ObjectUtil.isNotNull(deviceType)) {
            model.setDevice(deviceType.getDevice());
        }
        StpUtil.login(loginUser.getLoginId(), model.setExtra(USER_KEY, loginUser.getUserId()));
        StpUtil.getTokenSession().set(LOGIN_USER_KEY, loginUser);
    }

    /** 获取当前登录用户（请求级缓存 + token 会话） */
    public static LoginUser getLoginUser() {
        LoginUser loginUser = (LoginUser) SaHolder.getStorage().get(LOGIN_USER_KEY);
        if (loginUser != null) {
            return loginUser;
        }
        SaSession tokenSession = StpUtil.getTokenSession();
        if (tokenSession != null) {
            loginUser = (LoginUser) tokenSession.get(LOGIN_USER_KEY);
            SaHolder.getStorage().set(LOGIN_USER_KEY, loginUser);
        }
        return loginUser;
    }

    /** 按 token 取登录用户（供 MyBatis 审计填充等场景使用） */
    public static <T extends LoginUser> T getLoginUser(String token) {
        SaSession session = StpUtil.getTokenSessionByToken(token);
        if (ObjectUtil.isNull(session)) {
            return null;
        }
        return (T) session.get(LOGIN_USER_KEY);
    }

    /** 获取用户id */
    public static Long getUserId() {
        Long userId;
        try {
            userId = Convert.toLong(SaHolder.getStorage().get(USER_KEY));
            if (ObjectUtil.isNull(userId)) {
                try {
                    userId = Convert.toLong(StpUtil.getExtra(USER_KEY));
                } catch (Exception ignore) {
                    // 默认 token 模式下 getExtra 抛 ApiDisabledException（extra 仅 jwt 模式支持），忽略后走兜底
                }
            }
            if (ObjectUtil.isNull(userId)) {
                // 默认 token 模式下 getExtra 恒为 null（extra 仅 jwt 模式生效），从 token 会话中的 LoginUser 兜底
                LoginUser loginUser = getLoginUser();
                userId = loginUser != null ? loginUser.getUserId() : null;
            }
            if (ObjectUtil.isNotNull(userId)) {
                SaHolder.getStorage().set(USER_KEY, userId);
            }
        } catch (Exception e) {
            return null;
        }
        return userId;
    }

    /** 获取用户类型 */
    public static UserType getUserType() {
        String loginId = StpUtil.getLoginIdAsString();
        return UserType.getUserType(loginId);
    }
}
