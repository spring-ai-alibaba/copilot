package com.alibaba.cloud.ai.copilot.system.domain.bo;

import com.alibaba.cloud.ai.copilot.system.domain.SysUser;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 用户信息业务对象 sys_user
 *
 * @author Michelle.Chung
 */

@Data
@NoArgsConstructor
@AutoMapper(target = SysUser.class, reverseConvertGenerate = false)
public class SysUserBo {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户账号
     */
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 0, max = 30, message = "用户账号长度不能超过{max}个字符")
    private String userName;

    /**
     * 用户昵称
     */
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 用户类型（sys_user系统用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String sex;

    /**
     * 密码
     */
    private String password;

    /**
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 微信头像
     */
    private String avatar;

    /**
     * 备注
     */
    private String remark;

    /**
     * 注册域名
     */
    private String domainName;

    /** 普通用户的标识,对当前开发者帐号唯一。一个openid对应一个公众号或小程序 */
    private String openId;


    /** 用户余额 */
    private Double userBalance;
}
