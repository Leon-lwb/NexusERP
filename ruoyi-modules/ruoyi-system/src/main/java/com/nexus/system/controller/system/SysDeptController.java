package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysDeptBo;
import com.nexus.system.domain.vo.SysDeptVo;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理Controller
 * <p>提供部门（组织机构）的树形列表查询、详情、新增、修改、删除及部门选择框等接口，
 * 同时校验数据权限与层级约束。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController {

    /** 部门业务服务，提供部门树查询、唯一性校验、数据权限与增删改能力。 */
    private final ISysDeptService deptService;
    /** 岗位业务服务，用于删除部门前校验部门下是否存在关联岗位。 */
    private final ISysPostService postService;

    /**
     * 查询部门列表（树形结构）。
     *
     * @param dept 部门查询条件（部门名称、状态等）
     * @return 部门列表
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list")
    public R<List<SysDeptVo>> list(SysDeptBo dept) {
        List<SysDeptVo> depts = deptService.selectDeptList(dept);
        return R.ok(depts);
    }

    /**
     * 查询部门列表并排除指定节点及其所有子节点（用于部门选择上级时回显）。
     *
     * @param deptId 待排除的部门 ID（为空则不排除）
     * @return 排除后的部门列表
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list/exclude/{deptId}")
    public R<List<SysDeptVo>> excludeChild(@PathVariable(value = "deptId", required = false) Long deptId) {
        List<SysDeptVo> depts = deptService.selectDeptList(new SysDeptBo());
        depts.removeIf(d -> d.getDeptId().equals(deptId)
            || StringUtils.splitList(d.getAncestors()).contains(Convert.toStr(deptId)));
        return R.ok(depts);
    }

    /**
     * 根据部门编号获取部门详细信息（先校验数据权限）。
     *
     * @param deptId 部门 ID
     * @return 部门详情
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping(value = "/{deptId}")
    public R<SysDeptVo> getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return R.ok(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门，校验部门名称唯一性。
     *
     * @param dept 部门新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysDeptBo dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            return R.fail("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改部门，校验数据权限、名称唯一性、层级与停用约束。
     *
     * @param dept 部门修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysDeptBo dept) {
        Long deptId = dept.getDeptId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept)) {
            return R.fail("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        } else if (dept.getParentId().equals(deptId)) {
            return R.fail("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        } else if (StringUtils.equals(SystemConstants.DISABLE, dept.getStatus())) {
            if (deptService.selectNormalChildrenDeptById(deptId) > 0) {
                return R.fail("该部门包含未停用的子部门!");
            } else if (deptService.checkDeptExistUser(deptId)) {
                return R.fail("该部门下存在已分配用户，不能禁用!");
            }
        }
        return toAjax(deptService.updateDept(dept));
    }

    /**
     * 删除部门，前置校验默认部门、下级部门、部门用户与部门岗位约束。
     *
     * @param deptId 部门 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public R<Void> remove(@PathVariable Long deptId) {
        if (SystemConstants.DEFAULT_DEPT_ID.equals(deptId)) {
            return R.warn("默认部门,不允许删除");
        }
        if (deptService.hasChildByDeptId(deptId)) {
            return R.warn("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            return R.warn("部门存在用户,不允许删除");
        }
        if (postService.countPostByDeptId(deptId) > 0) {
            return R.warn("部门存在岗位,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        return toAjax(deptService.deleteDeptById(deptId));
    }

    /**
     * 获取部门选择框列表（支持按部门 ID 串预选）。
     *
     * @param deptIds 预选部门 ID 数组（为空则查全部）
     * @return 部门列表
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping("/optionselect")
    public R<List<SysDeptVo>> optionselect(@RequestParam(required = false) Long[] deptIds) {
        return R.ok(deptService.selectDeptByIds(deptIds == null ? null : List.of(deptIds)));
    }

}
