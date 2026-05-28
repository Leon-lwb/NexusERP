package com.nexus.workflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.annotation.DataColumn;
import com.nexus.common.mybatis.annotation.DataPermission;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 身份Mapper接口
 *
 * @author wanghuan
 */
public interface WfIdentityMapper {

    @DataPermission({
            @DataColumn(key = "deptName", value = "d.dept_id"),
            @DataColumn(key = "userName", value = "u.user_id")
    })
    Page<Map<String, Object>> selectPageUserList(@Param("page") Page<?> page, @Param("deptId") Long deptId);

    @DataPermission({
            @DataColumn(key = "deptName", value = "d.dept_id")
    })
    List<Map<String, Object>> selectDeptList();
}

