package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
import com.nexus.system.domain.bo.SysClientBo;
import com.nexus.system.domain.vo.SysClientVo;
import com.nexus.system.service.ISysClientService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户端管理Controller
 * <p>提供系统客户端（OAuth2 客户端）的增删改查、状态变更与 Excel 导出接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author Michelle.Chung
 * @date 2023-06-18
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/client")
public class SysClientController extends BaseController {

    /** 客户端业务服务，提供分页查询、唯一性校验、增删改及状态变更等能力。 */
    private final ISysClientService sysClientService;

    /**
     * 分页查询客户端列表。
     *
     * @param bo        客户端查询条件（客户端 key、名称等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 客户端分页结果
     */
    @SaCheckPermission("system:client:list")
    @GetMapping("/list")
    public TableDataInfo<SysClientVo> list(SysClientBo bo, PageQuery pageQuery) {
        return sysClientService.queryPageList(bo, pageQuery);
    }

    /**
     * 导出客户端列表为 Excel 文件。
     *
     * @param bo       客户端查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @SaCheckPermission("system:client:export")
    @Log(title = "客户端管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SysClientBo bo, HttpServletResponse response) {
        List<SysClientVo> list = sysClientService.queryList(bo);
        ExcelUtil.exportExcel(list, "客户端管理", SysClientVo.class, response);
    }

    /**
     * 获取指定客户端详细信息。
     *
     * @param id 客户端主键
     * @return 客户端详情
     */
    @SaCheckPermission("system:client:query")
    @GetMapping("/{id}")
    public R<SysClientVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return R.ok(sysClientService.queryById(id));
    }

    /**
     * 新增客户端，校验客户端 key 唯一性。
     *
     * @param bo 客户端新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:client:add")
    @Log(title = "客户端管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            return R.fail("新增客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        return toAjax(sysClientService.insertByBo(bo));
    }

    /**
     * 修改客户端，校验客户端 key 唯一性。
     *
     * @param bo 客户端修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:client:edit")
    @Log(title = "客户端管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            return R.fail("修改客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        return toAjax(sysClientService.updateByBo(bo));
    }

    /**
     * 修改客户端状态（启用/停用）。
     *
     * @param bo 含客户端 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:client:edit")
    @Log(title = "客户端管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysClientBo bo) {
        return toAjax(sysClientService.updateClientStatus(bo.getClientId(), bo.getStatus()));
    }

    /**
     * 批量删除客户端。
     *
     * @param ids 客户端主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:client:remove")
    @Log(title = "客户端管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        return toAjax(sysClientService.deleteWithValidByIds(List.of(ids), true));
    }
}
