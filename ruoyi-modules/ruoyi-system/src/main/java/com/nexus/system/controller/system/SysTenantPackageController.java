package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.nexus.common.core.constant.TenantConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysTenantPackageBo;
import com.nexus.system.domain.vo.SysTenantPackageVo;
import com.nexus.system.service.ISysTenantPackageService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户套餐管理Controller
 * <p>提供租户套餐（含菜单权限、功能权限集合）的分页查询、下拉列表、Excel 导出、详情、增删改与状态启停等接口。
 * 仅限超管角色访问，且需在开启租户模式（tenant.enable=true）时生效。属于系统管理（System）模块。</p>
 *
 * @author Michelle.Chung
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/tenant/package")
@ConditionalOnProperty(value = "tenant.enable", havingValue = "true")
public class SysTenantPackageController extends BaseController {

    /** 租户套餐业务服务，提供分页、唯一性校验、增删改与状态变更等能力。 */
    private final ISysTenantPackageService tenantPackageService;

    /**
     * 分页查询租户套餐列表。
     *
     * @param bo        套餐查询条件（套餐名称、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 租户套餐分页结果
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:list")
    @GetMapping("/list")
    public TableDataInfo<SysTenantPackageVo> list(SysTenantPackageBo bo, PageQuery pageQuery) {
        return tenantPackageService.queryPageList(bo, pageQuery);
    }

    /**
     * 查询租户套餐下拉选择列表。
     *
     * @return 全部租户套餐列表
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:list")
    @GetMapping("/selectList")
    public R<List<SysTenantPackageVo>> selectList() {
        return R.ok(tenantPackageService.selectList());
    }

    /**
     * 导出租户套餐列表为 Excel 文件。
     *
     * @param bo       套餐查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:export")
    @Log(title = "租户套餐", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SysTenantPackageBo bo, HttpServletResponse response) {
        List<SysTenantPackageVo> list = tenantPackageService.queryList(bo);
        ExcelUtil.exportExcel(list, "租户套餐", SysTenantPackageVo.class, response);
    }

    /**
     * 根据套餐 ID 查询租户套餐详细信息。
     *
     * @param packageId 套餐主键
     * @return 租户套餐详情
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:query")
    @GetMapping("/{packageId}")
    public R<SysTenantPackageVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long packageId) {
        return R.ok(tenantPackageService.queryById(packageId));
    }

    /**
     * 新增租户套餐，校验套餐名称唯一性。
     *
     * @param bo 套餐新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:add")
    @Log(title = "租户套餐", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysTenantPackageBo bo) {
        if (!tenantPackageService.checkPackageNameUnique(bo)) {
            return R.fail("新增套餐'" + bo.getPackageName() + "'失败，套餐名称已存在");
        }
        return toAjax(tenantPackageService.insertByBo(bo));
    }

    /**
     * 修改租户套餐，校验套餐名称唯一性。
     *
     * @param bo 套餐修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:edit")
    @Log(title = "租户套餐", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysTenantPackageBo bo) {
        if (!tenantPackageService.checkPackageNameUnique(bo)) {
            return R.fail("修改套餐'" + bo.getPackageName() + "'失败，套餐名称已存在");
        }
        return toAjax(tenantPackageService.updateByBo(bo));
    }

    /**
     * 启停租户套餐状态。
     *
     * @param bo 含套餐 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:edit")
    @Log(title = "租户套餐", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysTenantPackageBo bo) {
        return toAjax(tenantPackageService.updatePackageStatus(bo));
    }

    /**
     * 批量删除租户套餐。
     *
     * @param packageIds 套餐主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenantPackage:remove")
    @Log(title = "租户套餐", businessType = BusinessType.DELETE)
    @DeleteMapping("/{packageIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] packageIds) {
        return toAjax(tenantPackageService.deleteWithValidByIds(List.of(packageIds), true));
    }
}
