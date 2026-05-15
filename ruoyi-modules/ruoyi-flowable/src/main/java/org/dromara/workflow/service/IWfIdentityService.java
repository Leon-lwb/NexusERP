package org.dromara.workflow.service;

import cn.hutool.core.lang.tree.Tree;

import java.util.List;
import java.util.Map;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 部门管理 服务层
 *
 * @author wanghuan
 */
public interface IWfIdentityService {

    List<Tree<Long>> selectDeptTreeList();

    TableDataInfo<Map<String, Object>> selectPageUserList(Long deptId, PageQuery pageQuery);
}
