package com.nexus.system.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommonMapper extends BaseMapper<Object> {
	//根据角色id获取用户列表id
    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdByRoleId(@Param("roleId") Long roleId);
}
