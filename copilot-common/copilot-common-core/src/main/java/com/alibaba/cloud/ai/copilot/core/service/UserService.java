package com.alibaba.cloud.ai.copilot.core.service;

/**
 * 通用 用户服务
 *
 * @author yzm
 */
public interface UserService {

    /**
     * 通过用户ID查询用户账户
     *
     * @param userId 用户ID
     * @return 用户账户
     */
    String selectUserNameById(Long userId);

    /**
     * 通过用户名称查询余额
     *
     * @param userName
     * @return
     */
    String selectUserByName(String userName);
}