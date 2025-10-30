package com.alibaba.cloud.ai.copilot.system.service;

import cn.dev33.satoken.secure.BCrypt;
import com.alibaba.cloud.ai.copilot.core.constant.Constants;
import com.alibaba.cloud.ai.copilot.core.constant.GlobalConstants;
import com.alibaba.cloud.ai.copilot.core.domain.LogininforEvent;
import com.alibaba.cloud.ai.copilot.core.domain.model.RegisterBody;
import com.alibaba.cloud.ai.copilot.core.exception.base.BaseException;
import com.alibaba.cloud.ai.copilot.core.exception.user.CaptchaException;
import com.alibaba.cloud.ai.copilot.core.exception.user.CaptchaExpireException;
import com.alibaba.cloud.ai.copilot.core.exception.user.UserException;
import com.alibaba.cloud.ai.copilot.core.utils.MessageUtils;
import com.alibaba.cloud.ai.copilot.core.utils.ServletUtils;
import com.alibaba.cloud.ai.copilot.core.utils.SpringUtils;
import com.alibaba.cloud.ai.copilot.core.utils.StringUtils;
import com.alibaba.cloud.ai.copilot.redis.utils.RedisUtils;
import com.alibaba.cloud.ai.copilot.system.domain.SysUser;
import com.alibaba.cloud.ai.copilot.system.domain.bo.SysUserBo;
import com.alibaba.cloud.ai.copilot.system.domain.vo.SysUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 注册校验方法
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysRegisterService {

    private final ISysUserService userService;

    /**
     * 注册
     */
    public void register(RegisterBody registerBody) {

        String username = registerBody.getUsername();
        String password = registerBody.getPassword();

        // 检查验证码是否正确
//        validateEmail(username,registerBody.getCode());
        SysUserBo sysUser = new SysUserBo();
        sysUser.setDomainName(registerBody.getDomainName());
        sysUser.setUserName(username);
        sysUser.setNickName(username);
        sysUser.setPassword(BCrypt.hashpw(password));
        if (!userService.checkUserNameUnique(sysUser)) {
            throw new UserException("user.username.registered", username);
        }
        sysUser.setUserBalance(1.0);
        SysUser user = userService.registerUser(sysUser);
        if (user == null) {
            throw new UserException("用户注册失败!");
        }
        recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    /**
     * 重置密码
     */
    public void resetPassWord(RegisterBody registerBody) {
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        SysUserVo user = userService.selectUserByUserName(username);
        if(user == null){
            throw new UserException(String.format("用户【%s】,未注册!",username));
        }
        // 检查验证码是否正确
        validateEmail(username,registerBody.getCode());
        userService.resetUserPwd(user.getUserId(),BCrypt.hashpw(password));
    }

    /**
     * 校验邮箱验证码
     *
     * @param username 用户名
     */
    public void validateEmail(String username,String code) {
        String key = GlobalConstants.CAPTCHA_CODE_KEY + username;
         String captcha = RedisUtils.getCacheObject(key);
        if(code.equals(captcha)){
            RedisUtils.deleteObject(captcha);
        }else {
            throw new BaseException("验证码错误,请重试！");
        }
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String tenantId, String username, String code, String uuid) {
        String verifyKey = GlobalConstants.CAPTCHA_CODE_KEY + StringUtils.defaultString(uuid, "");
        String captcha = RedisUtils.getCacheObject(verifyKey);
        RedisUtils.deleteObject(verifyKey);
        if (captcha == null) {
            recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha)) {
            recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     * @return
     */
    private void recordLogininfor(String username, String status, String message) {
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(status);
        logininforEvent.setMessage(message);
        logininforEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(logininforEvent);
    }

}
