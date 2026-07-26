package com.nexus.mes.tg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.vo.TgMaintenanceRecordVo;
import com.nexus.mes.tg.service.ITgMaintenanceRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES工装保养记录 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/tg/maintenance-record")
public class TgMaintenanceRecordController extends BaseController {

    private final ITgMaintenanceRecordService tgMaintenanceRecordService;

    @SaCheckPermission("mes:tg:record:list")
    @GetMapping("/list")
    public TableDataInfo<TgMaintenanceRecordVo> list(TgMaintenanceRecordBo bo, PageQuery pageQuery) {
        return tgMaintenanceRecordService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:tg:record:query")
    @GetMapping("/{recordId}")
    public R<TgMaintenanceRecordVo> getInfo(@PathVariable Long recordId) {
        return R.ok(tgMaintenanceRecordService.selectById(recordId));
    }

    @SaCheckPermission("mes:tg:record:add")
    @Log(title = "工装保养记录新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TgMaintenanceRecordBo bo) {
        return toAjax(tgMaintenanceRecordService.insertByBo(bo));
    }

    @SaCheckPermission("mes:tg:record:edit")
    @Log(title = "工装保养记录修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TgMaintenanceRecordBo bo) {
        return toAjax(tgMaintenanceRecordService.updateByBo(bo));
    }

    @SaCheckPermission("mes:tg:record:remove")
    @Log(title = "工装保养记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(tgMaintenanceRecordService.deleteByIds(ids));
    }
}
