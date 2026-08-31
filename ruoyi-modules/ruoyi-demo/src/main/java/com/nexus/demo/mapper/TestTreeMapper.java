package com.nexus.demo.mapper;

import com.nexus.common.mybatis.annotation.DataColumn;
import com.nexus.common.mybatis.annotation.DataPermission;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.demo.domain.TestTree;
import com.nexus.demo.domain.vo.TestTreeVo;

/**
 * 测试树表Mapper接口
 *
 * @author wanghuan
 * @date 2021-07-26
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "dept_id"),
    @DataColumn(key = "userName", value = "user_id")
})
public interface TestTreeMapper extends BaseMapperPlus<TestTree, TestTreeVo> {

}
