package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.TenantConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.encrypt.annotation.ApiEncrypt;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysTenantBo;
import com.nexus.system.domain.vo.SysTenantVo;
import com.nexus.system.service.ISysTenantService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理Controller
 * <p>提供租户（多租户企业）的分页查询、Excel 导出、详情、增删改、状态启停、动态切换及数据同步等接口。
 * 仅限超管角色访问，且需在开启租户模式（tenant.enable=true）时生效。属于系统管理（System）模块。</p>
 *
 * @author Michelle.Chung
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/tenant")
@ConditionalOnProperty(value = "tenant.enable", havingValue = "true")
public class SysTenantController extends BaseController {

    /** 租户业务服务，提供分页、唯一性校验、增删改、状态变更与数据同步等能力。 */
    private final ISysTenantService tenantService;

    /**
     * 分页查询租户列表。
     *
     * @param bo        租户查询条件（企业名称、社会信用代码等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 租户分页结果
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:list")
    @GetMapping("/list")
    public TableDataInfo<SysTenantVo> list(SysTenantBo bo, PageQuery pageQuery) {
        return tenantService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出租户列表为 Excel 文件。
     *
     * @param bo       租户查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:export")
    @Log(title = "租户管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SysTenantBo bo, HttpServletResponse response) {
        List<SysTenantVo> list = tenantService.queryList(bo);
        ExcelUtil.exportExcel(list, "租户", SysTenantVo.class, response);
    }

    /**
     * 根据租户 ID 查询租户详细信息。
     *
     * @param id 租户主键
     * @return 租户详情
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:query")
    @GetMapping("/{id}")
    public R<SysTenantVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return R.ok(tenantService.queryById(id));
    }

    /**
     * 新增租户，校验企业名称唯一性（新增参数加密传输）。
     *
     * @param bo 租户新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @ApiEncrypt
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:add")
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @Lock4j
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysTenantBo bo) {
        if (!tenantService.checkCompanyNameUnique(bo)) {
            return R.fail("新增租户'" + bo.getCompanyName() + "'失败，企业名称已存在");
        }
        return toAjax(TenantHelper.ignore(() -> tenantService.insertByBo(bo)));
    }

    /**
     * 修改租户，校验租户可操作性与企业名称唯一性。
     *
     * @param bo 租户修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:edit")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysTenantBo bo) {
        tenantService.checkTenantAllowed(bo.getTenantId());
        if (!tenantService.checkCompanyNameUnique(bo)) {
            return R.fail("修改租户'" + bo.getCompanyName() + "'失败，公司名称已存在");
        }
        return toAjax(tenantService.updateByBo(bo));
    }

    /**
     * 启停租户状态。
     *
     * @param bo 含租户 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:edit")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysTenantBo bo) {
        tenantService.checkTenantAllowed(bo.getTenantId());
        return toAjax(tenantService.updateTenantStatus(bo));
    }

    /**
     * 批量删除租户。
     *
     * @param ids 租户主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:remove")
    @Log(title = "租户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(tenantService.deleteWithValidByIds(List.of(ids), true));
    }

    /**
     * 动态切换当前操作的租户上下文。
     *
     * @param tenantId 目标租户 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @GetMapping("/dynamic/{tenantId}")
    public R<Void> dynamicTenant(@NotBlank(message = "租户ID不能为空") @PathVariable String tenantId) {
        TenantHelper.setDynamic(tenantId, true);
        return R.ok();
    }

    /**
     * 清除当前动态租户上下文，恢复默认租户。
     *
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @GetMapping("/dynamic/clear")
    public R<Void> dynamicClear() {
        TenantHelper.clearDynamic();
        return R.ok();
    }


    /**
     * 同步指定租户的套餐菜单权限。
     *
     * @param tenantId  目标租户 ID
     * @param packageId 套餐 ID
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @SaCheckPermission("system:tenant:edit")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @Lock4j
    @GetMapping("/syncTenantPackage")
    public R<Void> syncTenantPackage(@NotBlank(message = "租户ID不能为空") String tenantId,
                                     @NotNull(message = "套餐ID不能为空") Long packageId) {
        return toAjax(TenantHelper.ignore(() -> tenantService.syncTenantPackage(tenantId, packageId)));
    }

    /**
     * 同步租户字典数据（仅开启租户模式时可用）。
     *
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @Lock4j
    @GetMapping("/syncTenantDict")
    public R<Void> syncTenantDict() {
        if (!TenantHelper.isEnable()) {
            return R.fail("当前未开启租户模式");
        }
        tenantService.syncTenantDict();
        return R.ok("同步租户字典成功");
    }

    /**
     * 同步租户参数配置（仅开启租户模式时可用）。
     *
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckRole(TenantConstants.SUPER_ADMIN_ROLE_KEY)
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @Lock4j
    @GetMapping("/syncTenantConfig")
    public R<Void> syncTenantConfig() {
        if (!TenantHelper.isEnable()) {
            return R.fail("当前未开启租户模式");
        }
        tenantService.syncTenantConfig();
        return R.ok("同步租户参数配置成功");
    }

}
