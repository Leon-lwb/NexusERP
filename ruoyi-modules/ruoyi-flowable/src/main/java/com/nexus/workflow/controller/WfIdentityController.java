package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.lang.tree.Tree;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.workflow.service.IWfIdentityService;

import java.util.List;
import java.util.Map;

/**
 * 身份控制器
 * <p>提供部门树与用户选择列表等流程候选人/部门查询 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/identity")
public class WfIdentityController {

    /** 流程身份服务，提供部门树与用户选择等查询能力。 */
    private final IWfIdentityService identityService;

    /**
     * 查询部门树列表。
     *
     * @return 部门树结构
     */
    @GetMapping("deptTree")
    public R<List<Tree<Long>>> deptTree() {
        return R.ok(identityService.selectDeptTreeList());
    }

    /**
     * 查询用户列表，用于用户选择场景。
     *
     * @param deptId    部门 ID（为空表示查询全部）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含用户列表及分页元信息
     */
    @SaCheckLogin
    @GetMapping("/selectUser")
    public TableDataInfo<Map<String, Object>> selectUser(Long deptId, PageQuery pageQuery) {
        return identityService.selectPageUserList(deptId, pageQuery);
    }
}
