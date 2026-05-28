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

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/autocode/part")
public class SysAutoCodePartController extends BaseController {

    private final ISysAutoCodePartService partService;

    @SaCheckPermission("system:autocode:part:list")
    @GetMapping("/list")
    public TableDataInfo<SysAutoCodePart> list(SysAutoCodePart part) {
        return TableDataInfo.build(partService.listPart(part));
    }

    @SaCheckPermission("system:autocode:part:query")
    @GetMapping("/{partId}")
    public R<SysAutoCodePartVo> getInfo(@PathVariable Long partId) {
        return R.ok(partService.findById(partId));
    }

    @SaCheckPermission("system:autocode:part:add")
    @Log(title = "编码规则组成", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysAutoCodePart part) {
        if (!partService.checkPartUnique(part)) {
            return R.fail("规则组成不唯一，请检查组成编码、组成名称、组成序号");
        }
        return toAjax(partService.insertPart(part));
    }

    @SaCheckPermission("system:autocode:part:edit")
    @Log(title = "编码规则组成", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysAutoCodePart part) {
        if (!partService.checkPartUnique(part)) {
            return R.fail("规则组成不唯一，请检查组成编码、组成名称、组成序号");
        }
        return toAjax(partService.updatePart(part));
    }

    @SaCheckPermission("system:autocode:part:remove")
    @Log(title = "编码规则组成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{partIds}")
    public R<Void> remove(@PathVariable Long[] partIds) {
        return toAjax(partService.deleteByIds(partIds));
    }
}
