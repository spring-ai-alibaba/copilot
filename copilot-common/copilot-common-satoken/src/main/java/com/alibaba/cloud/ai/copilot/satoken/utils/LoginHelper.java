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
        SaStorage storage = SaHolder.getStorage();
        Long userId = Convert.toLong(storage.get(USER_KEY));
        if (ObjectUtil.isNotNull(userId)) {
            return userId;
        }

        // Sa-Token 的 extra 在部分请求链路中可能不可用，先尝试读取，失败后再从
        // token session 中保存的 LoginUser 回退，避免已登录请求得到 null userId。
        try {
            userId = Convert.toLong(StpUtil.getExtra(USER_KEY));
        } catch (Exception ignored) {
            // 继续尝试 token session。
        }

        if (ObjectUtil.isNull(userId)) {
            try {
                LoginUser loginUser = getLoginUser();
                userId = loginUser != null ? loginUser.getUserId() : null;
            } catch (Exception ignored) {
                // 未登录或 token session 不可用时返回 null。
            }
        }

        if (ObjectUtil.isNotNull(userId)) {
            storage.set(USER_KEY, userId);
        }
        return userId;
    }

    /** 获取用户类型 */
    public static UserType getUserType() {
        String loginId = StpUtil.getLoginIdAsString();
        return UserType.getUserType(loginId);
    }
}
