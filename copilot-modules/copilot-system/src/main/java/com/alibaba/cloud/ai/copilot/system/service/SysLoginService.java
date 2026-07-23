package com.alibaba.cloud.ai.copilot.system.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.cloud.ai.copilot.core.domain.model.LoginUser;
import com.alibaba.cloud.ai.copilot.core.enums.DeviceType;
import com.alibaba.cloud.ai.copilot.core.enums.UserStatus;
import com.alibaba.cloud.ai.copilot.core.exception.user.UserException;
import com.alibaba.cloud.ai.copilot.core.utils.DateUtils;
import com.alibaba.cloud.ai.copilot.core.utils.ServletUtils;
import com.alibaba.cloud.ai.copilot.satoken.utils.LoginHelper;
import com.alibaba.cloud.ai.copilot.system.domain.SysUser;
import com.alibaba.cloud.ai.copilot.system.domain.vo.SysUserVo;
import com.alibaba.cloud.ai.copilot.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录校验方法（精简版：仅用户名/密码登录与登出，无验证码、无失败计数、无 Redis）
 *
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class SysLoginService {

    private final SysUserMapper userMapper;

    /** 登录验证 */
    public String login(String username, String password) {
        SysUserVo user = loadUserByUsername(username);
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new UserException("user.password.not.match");
        }
        LoginUser loginUser = buildLoginUser(user);
        LoginHelper.loginByDevice(loginUser, DeviceType.PC);
        recordLoginInfo(user.getUserId());
        return StpUtil.getTokenValue();
    }

    /** 退出登录 */
    public void logout() {
        try {
            StpUtil.logout();
        } catch (NotLoginException ignored) {
        }
    }

    private LoginUser buildLoginUser(SysUserVo user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUserName());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setUserType(user.getUserType());
        return loginUser;
    }

    private SysUserVo loadUserByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .select(SysUser::getUserName, SysUser::getStatus)
            .eq(SysUser::getUserName, username));
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", username);
            throw new UserException("user.not.exists", username);
        } else if (UserStatus.DISABLE.getCode().equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", username);
            throw new UserException("user.blocked", username);
        }
        return userMapper.selectUserByUserName(username);
    }

    /** 记录登录 IP / 时间 */
    public void recordLoginInfo(Long userId) {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setLoginIp(ServletUtils.getClientIP());
        sysUser.setLoginDate(DateUtils.getNowDate());
        userMapper.updateById(sysUser);
    }
}
