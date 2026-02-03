package com.nexus.workflow.service;

import cn.hutool.core.lang.tree.Tree;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;

import java.util.List;
import java.util.Map;

/**
 * 部门管理 服务层
 *
 * @author nbacheng
 */
public interface IWfIdentityService {

    List<Tree<Long>> selectDeptTreeList();

    WFTableDataInfo<Map<String, Object>> selectPageUserList(Long deptId, PageQuery pageQuery);
}
