package com.alibaba.cloud.ai.copilot.system.service;


import com.alibaba.cloud.ai.copilot.system.domain.SysUser;
import com.alibaba.cloud.ai.copilot.system.domain.bo.SysUserBo;
import com.alibaba.cloud.ai.copilot.system.domain.vo.SysUserVo;

/**
 * 用户 业务层
 *
 * @author Lion Li
 */
public interface ISysUserService {

    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    SysUserVo selectUserByUserName(String userName);

    /**
     * 通过OpenId查询用户
     *
     * @Date 2023/5/18
     * @param openId
     * @return SysUserVo
     **/
    SysUserVo selectUserByOpenId(String openId);

    /**
     * 通过手机号查询用户
     *
     * @param phoneNumber 手机号
     * @return 用户对象信息
     */
    SysUserVo selectUserByPhoneNumber(String phoneNumber);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    SysUserVo selectUserById(Long userId);

    /**
     * 校验用户名称是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkUserNameUnique(SysUserBo user);

    /**
     * 校验手机号码是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkPhoneUnique(SysUserBo user);

    /**
     * 校验email是否唯一
     *
     * @param user 用户信息
     * @return 结果
     */
    boolean checkEmailUnique(SysUserBo user);


    /**
     * 新增用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int insertUser(SysUserBo user);

    /**
     * 注册用户信息
     **/
    SysUser registerUser(SysUserBo user);

    /**
     * 修改用户信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int updateUser(SysUserBo user);

    /**
     * 修改用户状态
     *
     * @param userId 用户ID
     * @param status 帐号状态
     * @return 结果
     */
    int updateUserStatus(Long userId, String status);

    /**
     * 修改用户基本信息
     *
     * @param user 用户信息
     * @return 结果
     */
    int updateUserProfile(SysUserBo user);

    /**
     * 修改用户头像
     *
     * @param userId 用户ID
     * @param avatar 头像地址
     * @return 结果
     */
    boolean updateUserAvatar(Long userId, String avatar);

    boolean updateUserName(Long userId, String nickName);

    /**
     * 重置用户密码
     *
     * @param userId   用户ID
     * @param password 密码
     * @return 结果
     */

    int resetUserPwd(Long userId, String password);

}
