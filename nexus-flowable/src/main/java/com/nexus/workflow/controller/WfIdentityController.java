package com.nexus.workflow.controller;

import cn.hutool.core.lang.tree.Tree;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.service.IWfIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 身份控制器
 *
 * @author nbacheng
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/identity")
public class WfIdentityController {

    private final IWfIdentityService identityService;

    @GetMapping("deptTree")
    public R<List<Tree<Long>>> deptTree() {
        return R.ok(identityService.selectDeptTreeList());
    }

    /**
     * 查询用户列表，用于用户选择场景
     */
    @GetMapping("/selectUser")
    public WFTableDataInfo<Map<String, Object>> selectUser(Long deptId, PageQuery pageQuery) {
        return identityService.selectPageUserList(deptId, pageQuery);
    }
}
