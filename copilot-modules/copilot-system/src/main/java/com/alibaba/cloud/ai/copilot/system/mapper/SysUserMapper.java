package com.alibaba.cloud.ai.copilot.system.mapper;

import com.alibaba.cloud.ai.copilot.mybatis.core.mapper.BaseMapperPlus;
import com.alibaba.cloud.ai.copilot.system.domain.SysUser;
import com.alibaba.cloud.ai.copilot.system.domain.vo.SysUserVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 数据层
 */
@Mapper
public interface SysUserMapper extends BaseMapperPlus<SysUser, SysUserVo> {



    /**
     * 通过用户名查询用户
     *
     * @param userName 用户名
     * @return 用户对象信息
     */
    SysUserVo selectUserByUserName(String userName);

    /**
     * 通过用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户对象信息
     */
    SysUserVo selectUserById(Long userId);

    /**
     * 通过OpenId查询用户
     *
     * @param OpenId 微信用户唯一标识
     * @return 用户对象信息
     */
    SysUserVo selectUserByOpenId(String OpenId);

    /**
     * 通过手机号查询用户
     *
     * @param phonenumber 手机号
     * @return 用户对象信息
     */
    SysUserVo selectUserByPhonenumber(String phonenumber);
}
