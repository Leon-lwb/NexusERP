package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.core.validate.QueryGroup;
import com.nexus.common.web.core.BaseController;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.system.domain.bo.SysOssConfigBo;
import com.nexus.system.domain.vo.SysOssConfigVo;
import com.nexus.system.service.ISysOssConfigService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对象存储配置Controller
 * <p>提供 OSS 存储配置的分页查询、详情、增删改、状态启停等接口。
 * 属于系统管理（System）模块中的资源管理部分。</p>
 *
 * @author wanghuan
 * @author 孤舟烟雨
 * @date 2021-08-13
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/oss/config")
public class SysOssConfigController extends BaseController {

    /** OSS 配置业务服务，提供分页、详情、增删改与状态变更等能力。 */
    private final ISysOssConfigService ossConfigService;

    /**
     * 分页查询对象存储配置列表。
     *
     * @param bo        存储配置查询条件（配置名称、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 存储配置分页结果
     */
    @SaCheckPermission("system:ossConfig:list")
    @GetMapping("/list")
    public TableDataInfo<SysOssConfigVo> list(@Validated(QueryGroup.class) SysOssConfigBo bo, PageQuery pageQuery) {
        return ossConfigService.queryPageList(bo, pageQuery);
    }

    /**
     * 根据配置 ID 查询对象存储配置详细信息。
     *
     * @param ossConfigId OSS 配置主键
     * @return 存储配置详情
     */
    @SaCheckPermission("system:ossConfig:list")
    @GetMapping("/{ossConfigId}")
    public R<SysOssConfigVo> getInfo(@NotNull(message = "主键不能为空")
                                     @PathVariable Long ossConfigId) {
        return R.ok(ossConfigService.queryById(ossConfigId));
    }

    /**
     * 新增对象存储配置。
     *
     * @param bo 存储配置新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:ossConfig:add")
    @Log(title = "对象存储配置", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysOssConfigBo bo) {
        return toAjax(ossConfigService.insertByBo(bo));
    }

    /**
     * 修改对象存储配置。
     *
     * @param bo 存储配置修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:ossConfig:edit")
    @Log(title = "对象存储配置", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysOssConfigBo bo) {
        return toAjax(ossConfigService.updateByBo(bo));
    }

    /**
     * 批量删除对象存储配置。
     *
     * @param ossConfigIds OSS 配置主键数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:ossConfig:remove")
    @Log(title = "对象存储配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ossConfigIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ossConfigIds) {
        return toAjax(ossConfigService.deleteWithValidByIds(List.of(ossConfigIds), true));
    }

    /**
     * 启停对象存储配置状态。
     *
     * @param bo 含配置 ID 与状态值的参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:ossConfig:edit")
    @Log(title = "对象存储状态修改", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysOssConfigBo bo) {
        return toAjax(ossConfigService.updateOssConfigStatus(bo));
    }
}
