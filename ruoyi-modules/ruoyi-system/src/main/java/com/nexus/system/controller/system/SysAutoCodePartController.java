package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.vo.SysAutoCodePartVo;
import com.nexus.system.service.ISysAutoCodePartService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 编码规则组成Controller
 * <p>管理编码规则（sys_auto_code_rule）的各组成部分（流水号、日期、固定字符、传入字符）。
 * 属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/autocode/part")
public class SysAutoCodePartController extends BaseController {

    /** 编码规则组成业务服务，提供分页、详情、唯一性校验、增删改等能力。 */
    private final ISysAutoCodePartService partService;

    /**
     * 分页列表查询
     *
     * @param part 查询条件
     * @return 编码规则组成分页列表
     */
    @SaCheckPermission("system:autocode:part:list")
    @GetMapping("/list")
    public TableDataInfo<SysAutoCodePart> list(SysAutoCodePart part) {
        return TableDataInfo.build(partService.listPart(part));
    }

    /**
     * 根据 ID 查询编码规则组成详情
     *
     * @param partId 组成 ID
     * @return 详情
     */
    @SaCheckPermission("system:autocode:part:query")
    @GetMapping("/{partId}")
    public R<SysAutoCodePartVo> getInfo(@PathVariable Long partId) {
        return R.ok(partService.findById(partId));
    }

    /**
     * 新增编码规则组成
     *
     * @param part 编码规则组成实体
     * @return 结果
     */
    @SaCheckPermission("system:autocode:part:add")
    @Log(title = "编码规则组成", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysAutoCodePart part) {
        if (!partService.checkPartUnique(part)) {
            return R.fail("规则组成不唯一，请检查组成编码、组成名称、组成序号");
        }
        return toAjax(partService.insertPart(part));
    }

    /**
     * 修改编码规则组成
     *
     * @param part 编码规则组成实体
     * @return 结果
     */
    @SaCheckPermission("system:autocode:part:edit")
    @Log(title = "编码规则组成", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysAutoCodePart part) {
        if (!partService.checkPartUnique(part)) {
            return R.fail("规则组成不唯一，请检查组成编码、组成名称、组成序号");
        }
        return toAjax(partService.updatePart(part));
    }

    /**
     * 批量删除编码规则组成
     *
     * @param partIds 组成 ID 数组
     * @return 结果
     */
    @SaCheckPermission("system:autocode:part:remove")
    @Log(title = "编码规则组成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{partIds}")
    public R<Void> remove(@PathVariable Long[] partIds) {
        return toAjax(partService.deleteByIds(partIds));
    }
}
