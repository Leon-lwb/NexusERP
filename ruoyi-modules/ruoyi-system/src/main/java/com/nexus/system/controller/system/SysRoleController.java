package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.SysUserRole;
import com.nexus.system.domain.bo.SysDeptBo;
import com.nexus.system.domain.bo.SysRoleBo;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.vo.SysRoleVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysRoleService;
import com.nexus.system.service.ISysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理Controller
 * <p>提供角色的分页查询、Excel 导出、详情、增删改、数据权限、状态启停、用户授权与部门树加载等接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/role")
public class SysRoleController extends BaseController {

    /** 角色业务服务，提供分页、唯一性校验、数据权限与增删改能力。 */
    private final ISysRoleService roleService;
    /** 用户业务服务，提供角色已分配/未分配用户的查询能力。 */
    private final ISysUserService userService;
    /** 部门业务服务，用于加载角色数据权限对应的部门树。 */
    private final ISysDeptService deptService;

    /**
     * 分页查询角色列表。
     *
     * @param role      角色查询条件（角色名称、权限字符、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 角色分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public TableDataInfo<SysRoleVo> list(SysRoleBo role, PageQuery pageQuery) {
        return roleService.selectPageRoleList(role, pageQuery);
    }

    /**
     * 导出角色列表为 Excel 文件。
     *
     * @param role     角色查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:role:export")
    @PostMapping("/export")
    public void export(SysRoleBo role, HttpServletResponse response) {
        List<SysRoleVo> list = roleService.selectRoleList(role);
        ExcelUtil.exportExcel(list, "角色数据", SysRoleVo.class, response);
    }

    /**
     * 根据角色编号查询角色详细信息（先校验数据权限）。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    @SaCheckPermission("system:role:query")
    @GetMapping(value = "/{roleId}")
    public R<SysRoleVo> getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return R.ok(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色，校验内置角色不可操作及名称、权限字符唯一性。
     *
     * @param role 角色新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改并保存角色，校验可操作性、数据权限及名称、权限字符唯一性；
     * 修改成功后会清理该角色的在线用户会话。
     *
     * @param role 角色修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            return R.fail("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }

        if (roleService.updateRole(role) > 0) {
            roleService.cleanOnlineUserByRole(role.getRoleId());
            return R.ok();
        }
        return R.fail("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改并保存角色的数据权限（部门范围）。
     *
     * @param role 含角色 ID 与数据权限范围的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/dataScope")
    public R<Void> dataScope(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.authDataScope(role));
    }

    /**
     * 启停角色状态。
     *
     * @param role 含角色 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.updateRoleStatus(role.getRoleId(), role.getStatus()));
    }

    /**
     * 批量删除角色。
     *
     * @param roleIds 角色 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public R<Void> remove(@PathVariable Long[] roleIds) {
        return toAjax(roleService.deleteRoleByIds(List.of(roleIds)));
    }

    /**
     * 获取角色选择框列表，支持按角色 ID 串预选。
     *
     * @param roleIds 预选角色 ID 数组（为空则查全部）
     * @return 角色列表
     */
    @SaCheckPermission("system:role:query")
    @GetMapping("/optionselect")
    public R<List<SysRoleVo>> optionselect(@RequestParam(required = false) Long[] roleIds) {
        return R.ok(roleService.selectRoleByIds(roleIds == null ? null : List.of(roleIds)));
    }

    /**
     * 查询已分配指定角色的用户分页列表。
     *
     * @param user      用户查询条件
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 已分配用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/allocatedList")
    public TableDataInfo<SysUserVo> allocatedList(SysUserBo user, PageQuery pageQuery) {
        return userService.selectAllocatedList(user, pageQuery);
    }

    /**
     * 查询未分配指定角色的用户分页列表。
     *
     * @param user      用户查询条件
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 未分配用户分页结果
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/unallocatedList")
    public TableDataInfo<SysUserVo> unallocatedList(SysUserBo user, PageQuery pageQuery) {
        return userService.selectUnallocatedList(user, pageQuery);
    }

    /**
     * 取消单个用户的角色授权。
     *
     * @param userRole 用户与角色的关联关系
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/cancel")
    public R<Void> cancelAuthUser(@RequestBody SysUserRole userRole) {
        return toAjax(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消指定用户的角色授权。
     *
     * @param roleId  角色 ID
     * @param userIds 待取消授权的用户 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/cancelAll")
    public R<Void> cancelAuthUserAll(Long roleId, Long[] userIds) {
        return toAjax(roleService.deleteAuthUsers(roleId, userIds));
    }

    /**
     * 批量将用户授权给指定角色。
     *
     * @param roleId  角色 ID
     * @param userIds 待授权的用户 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authUser/selectAll")
    public R<Void> selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthUsers(roleId, userIds));
    }

    /**
     * 获取指定角色对应的部门树（含已选部门与部门下拉树）。
     *
     * @param roleId 角色 ID
     * @return 角色部门树选择信息，含已选部门 ID 与部门树
     */
    @SaCheckPermission("system:role:list")
    @GetMapping(value = "/deptTree/{roleId}")
    public R<DeptTreeSelectVo> roleDeptTreeselect(@PathVariable("roleId") Long roleId) {
        DeptTreeSelectVo selectVo = new DeptTreeSelectVo(
            deptService.selectDeptListByRoleId(roleId),
            deptService.selectDeptTreeList(new SysDeptBo()));
        return R.ok(selectVo);
    }

    /**
     * 角色部门树选择信息，包含已选部门 ID 列表与部门下拉树。
     *
     * @param checkedKeys 已选中的部门 ID 列表
     * @param depts       部门下拉树结构列表
     */
    public record DeptTreeSelectVo(List<Long> checkedKeys, List<Tree<Long>> depts) {}

}
