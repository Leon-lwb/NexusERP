package com.nexus.mes.tg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.tg.domain.bo.TgUsageRecordBo;
import com.nexus.mes.tg.domain.vo.TgUsageRecordVo;
import com.nexus.mes.tg.service.ITgUsageRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES工装使用/领用记录 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/tg/usage-record")
public class TgUsageRecordController extends BaseController {

    private final ITgUsageRecordService tgUsageRecordService;

    @SaCheckPermission("mes:tg:usage:list")
    @GetMapping("/list")
    public TableDataInfo<TgUsageRecordVo> list(TgUsageRecordBo bo, PageQuery pageQuery) {
        return tgUsageRecordService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:tg:usage:query")
    @GetMapping("/{usageId}")
    public R<TgUsageRecordVo> getInfo(@PathVariable Long usageId) {
        return R.ok(tgUsageRecordService.selectById(usageId));
    }

    @SaCheckPermission("mes:tg:usage:add")
    @Log(title = "工装使用记录新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TgUsageRecordBo bo) {
        return toAjax(tgUsageRecordService.insertByBo(bo));
    }

    @SaCheckPermission("mes:tg:usage:edit")
    @Log(title = "工装使用记录修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TgUsageRecordBo bo) {
        return toAjax(tgUsageRecordService.updateByBo(bo));
    }

    @SaCheckPermission("mes:tg:usage:remove")
    @Log(title = "工装使用记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(tgUsageRecordService.deleteByIds(ids));
    }
}
