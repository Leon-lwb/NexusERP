package com.nexus.common.sign.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;
import com.nexus.common.sign.service.IESSealGrantService;
import com.nexus.common.web.core.BaseController;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 用章授权Controller
 *
 * @author nexus
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/grant")
public class EsSealGrantController extends BaseController {

    private final IESSealGrantService grantService;

    /**
     * 分页查询用章授权列表
     * <p>权限标识：{@code system:sign:grant:list}</p>
     *
     * @param bo        查询参数（印章ID、被授权人/部门、业务场景、状态等）
     * @param pageQuery 分页参数
     * @return 用章授权分页列表
     */
    @SaCheckPermission("system:sign:grant:list")
    @GetMapping("/list")
    public TableDataInfo<SealGrantVo> list(SealGrantBo bo, PageQuery pageQuery) {
        return grantService.selectPageList(bo, pageQuery);
    }

    /**
     * 按印章查询授权列表
     * <p>权限标识：{@code system:sign:grant:query}</p>
     *
     * @param sealId 印章ID
     * @return 该印章的全部授权
     */
    @SaCheckPermission("system:sign:grant:query")
    @GetMapping("/by-seal/{sealId}")
    public R<List<SealGrantVo>> bySeal(@PathVariable Long sealId) {
        return R.ok(grantService.selectListBySeal(sealId));
    }

    /**
     * 新增用章授权
     * <p>权限标识：{@code system:sign:grant:add}</p>
     * <p>日志记录：title="用章授权"，businessType=INSERT</p>
     *
     * @param bo 用章授权业务对象
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:grant:add")
    @Log(title = "用章授权", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SealGrantBo bo) {
        grantService.insertByBo(bo);
        return R.ok();
    }

    /**
     * 修改用章授权
     * <p>权限标识：{@code system:sign:grant:edit}</p>
     * <p>日志记录：title="用章授权"，businessType=UPDATE</p>
     *
     * @param bo 用章授权业务对象
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:grant:edit")
    @Log(title = "用章授权", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SealGrantBo bo) {
        return toAjax(grantService.updateByBo(bo));
    }

    /**
     * 删除用章授权（逻辑删除）
     * <p>权限标识：{@code system:sign:grant:remove}</p>
     * <p>日志记录：title="用章授权"，businessType=DELETE</p>
     *
     * @param grantIds 授权ID数组
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:grant:remove")
    @Log(title = "用章授权", businessType = BusinessType.DELETE)
    @DeleteMapping("/{grantIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] grantIds) {
        return toAjax(grantService.deleteByIds(Arrays.asList(grantIds)));
    }
}
