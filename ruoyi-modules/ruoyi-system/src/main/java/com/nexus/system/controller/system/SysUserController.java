package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.utils.StreamUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.encrypt.annotation.ApiEncrypt;
import com.nexus.common.excel.core.ExcelResult;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.helper.DataPermissionHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysDeptBo;
import com.nexus.system.domain.bo.SysPostBo;
import com.nexus.system.domain.bo.SysRoleBo;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.vo.*;
import com.nexus.system.listener.SysUserImportListener;
import com.nexus.system.service.*;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理Controller
 * <p>提供用户的增删改查、Excel 导入导出、个人信息获取、密码重置、状态启停、角色授权与部门树查询等接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/user")
public class SysUserController extends BaseController {

    /** 用户业务服务，提供用户分页、唯一性校验、增删改与授权等能力。 */
    private final ISysUserService userService;
    /** 角色业务服务，用于用户角色查询与授权。 */
    private final ISysRoleService roleService;
    /** 岗位业务服务，用于查询用户所属岗位。 */
    private final ISysPostService postService;
    /** 部门业务服务，用于校验部门数据权限与查询部门树。 */
    private final ISysDeptService deptService;
    /** 租户业务服务，用于校验租户下用户名额。 */
    private final ISysTenantService tenantService;

    /**
     * 分页查询用户列表。
     *
     * @param user      用户查询条件（用户名、部门、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 用户分页结果
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/list")
    public TableDataInfo<SysUserVo> list(SysUserBo user, PageQuery pageQuery) {
        return userService.selectPageUserList(user, pageQuery);
    }

    /**
     * 导出用户列表为 Excel 文件。
     *
     * @param user     用户查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:user:export")
    @PostMapping("/export")
    public void export(SysUserBo user, HttpServletResponse response) {
        List<SysUserExportVo> list = userService.selectUserExportList(user);
        ExcelUtil.exportExcel(list, "用户数据", SysUserExportVo.class, response);
    }

    /**
     * 从 Excel 文件导入用户数据，支持更新已存在数据。
     *
     * @param file          导入的 Excel 文件
     * @param updateSupport 是否更新已存在的用户数据
     * @return 导入结果分析信息
     * @throws Exception 解析 Excel 异常时抛出
     */
    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission("system:user:import")
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> importData(@RequestPart("file") MultipartFile file, boolean updateSupport) throws Exception {
        ExcelResult<SysUserImportVo> result = ExcelUtil.importExcel(file.getInputStream(), SysUserImportVo.class, new SysUserImportListener(updateSupport));
        return R.ok(result.getAnalysis());
    }

    /**
     * 下载用户导入模板（空 Excel）。
     *
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.exportExcel(new ArrayList<>(), "用户数据", SysUserImportVo.class, response);
    }

    /**
     * 获取当前登录用户的个人信息，包含用户资料、角色与菜单权限。
     *
     * @return 当前登录用户的信息视图
     */
    @GetMapping("/getInfo")
    public R<UserInfoVo> getInfo() {
        UserInfoVo userInfoVo = new UserInfoVo();
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (TenantHelper.isEnable() && LoginHelper.isSuperAdmin()) {
            // 超级管理员 如果重新加载用户信息需清除动态租户
            TenantHelper.clearDynamic();
        }

        SysUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
        if (ObjectUtil.isNull(user)) {
            return R.fail("没有权限访问用户数据!");
        }
        userInfoVo.setUser(user);
        userInfoVo.setPermissions(loginUser.getMenuPermission());
        userInfoVo.setRoles(loginUser.getRolePermission());
        return R.ok(userInfoVo);
    }

    /**
     * 根据用户编号获取用户详细信息，含角色、岗位及可选的角色下拉列表。
     *
     * @param userId 用户 ID（为空时仅返回角色下拉列表）
     * @return 用户详细信息视图
     */
    @SaCheckPermission("system:user:query")
    @GetMapping(value = {"/", "/{userId}"})
    public R<SysUserInfoVo> getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        SysUserInfoVo userInfoVo = new SysUserInfoVo();
        if (ObjectUtil.isNotNull(userId)) {
            userService.checkUserDataScope(userId);
            SysUserVo sysUser = userService.selectUserById(userId);
            userInfoVo.setUser(sysUser);
            userInfoVo.setRoleIds(roleService.selectRoleListByUserId(userId));
            Long deptId = sysUser.getDeptId();
            if (ObjectUtil.isNotNull(deptId)) {
                SysPostBo postBo = new SysPostBo();
                postBo.setDeptId(deptId);
                userInfoVo.setPosts(postService.selectPostList(postBo));
                userInfoVo.setPostIds(postService.selectPostListByUserId(userId));
            }
        }
        SysRoleBo roleBo = new SysRoleBo();
        roleBo.setStatus(SystemConstants.NORMAL);
        List<SysRoleVo> roles = roleService.selectRoleList(roleBo);
        userInfoVo.setRoles(LoginHelper.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return R.ok(userInfoVo);
    }

    /**
     * 新增用户，校验账号/手机号/邮箱唯一性、部门数据权限与租户名额。
     *
     * @param user 用户新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysUserBo user) {
        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        if (TenantHelper.isEnable()) {
            if (!tenantService.checkAccountBalance(TenantHelper.getTenantId())) {
                return R.fail("当前租户下用户名额不足，请联系管理员");
            }
        }
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改用户，校验可操作性、数据权限、唯一性及部门权限。
     *
     * @param user 用户修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            return R.fail("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        return toAjax(userService.updateUser(user));
    }

    /**
     * 批量删除用户（不允许删除当前登录用户）。
     *
     * @param userIds 用户 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public R<Void> remove(@PathVariable Long[] userIds) {
        if (ArrayUtil.contains(userIds, LoginHelper.getUserId())) {
            return R.fail("当前用户不能删除");
        }
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 根据用户 ID 串或部门 ID 批量获取用户基础信息（选择框用）。
     *
     * @param userIds 预选用户 ID 数组（为空则按部门过滤）
     * @param deptId  部门 ID（用于查询该部门下的用户）
     * @return 用户列表
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/optionselect")
    public R<List<SysUserVo>> optionselect(@RequestParam(required = false) Long[] userIds,
                                           @RequestParam(required = false) Long deptId) {
        return R.ok(userService.selectUserByIds(ArrayUtil.isEmpty(userIds) ? null : List.of(userIds), deptId));
    }

    /**
     * 重置指定用户的登录密码（密码加密传输）。
     *
     * @param user 含用户 ID 与新密码的参数
     * @return 操作结果，成功时返回成功状态
     */
    @ApiEncrypt
    @SaCheckPermission("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/resetPwd")
    public R<Void> resetPwd(@RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        return toAjax(userService.resetUserPwd(user.getUserId(), user.getPassword()));
    }

    /**
     * 启停用户状态。
     *
     * @param user 含用户 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        return toAjax(userService.updateUserStatus(user.getUserId(), user.getStatus()));
    }

    /**
     * 获取指定用户已授权的角色列表（用于角色授权页回显）。
     *
     * @param userId 用户 ID
     * @return 含用户信息与可授权角色列表的视图
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/authRole/{userId}")
    public R<SysUserInfoVo> authRole(@PathVariable Long userId) {
        userService.checkUserDataScope(userId);
        SysUserVo user = userService.selectUserById(userId);
        List<SysRoleVo> roles = roleService.selectRolesAuthByUserId(userId);
        SysUserInfoVo userInfoVo = new SysUserInfoVo();
        userInfoVo.setUser(user);
        userInfoVo.setRoles(LoginHelper.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return R.ok(userInfoVo);
    }

    /**
     * 将角色批量授权给指定用户。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @RepeatSubmit()
    @PutMapping("/authRole")
    public R<Void> insertAuthRole(Long userId, Long[] roleIds) {
        userService.checkUserDataScope(userId);
        userService.insertUserAuth(userId, roleIds);
        return R.ok();
    }

    /**
     * 获取部门树列表（用于用户选择所属部门）。
     *
     * @param dept 部门查询条件
     * @return 部门树
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/deptTree")
    public R<List<Tree<Long>>> deptTree(SysDeptBo dept) {
        return R.ok(deptService.selectDeptTreeList(dept));
    }

    /**
     * 获取指定部门下的所有用户列表。
     *
     * @param deptId 部门 ID
     * @return 该部门下的用户列表
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/list/dept/{deptId}")
    public R<List<SysUserVo>> listByDept(@PathVariable @NotNull Long deptId) {
        return R.ok(userService.selectUserListByDept(deptId));
    }

}
