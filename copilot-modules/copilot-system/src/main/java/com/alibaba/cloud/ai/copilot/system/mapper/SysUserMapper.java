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
}
