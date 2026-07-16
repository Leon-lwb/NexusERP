package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;
import com.nexus.mes.eq.service.IEqMaintenanceRecordService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES设备保养执行记录 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/maintenance-record")
public class EqMaintenanceRecordController extends BaseController {

    private final IEqMaintenanceRecordService eqMaintenanceRecordService;

    /**
     * 分页查询保养执行记录列表
     */
    @SaCheckPermission("mes:eq:maintenance:record")
    @GetMapping("/list")
    public TableDataInfo<EqMaintenanceRecordVo> list(EqMaintenanceRecordBo bo, PageQuery pageQuery) {
        return eqMaintenanceRecordService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取保养执行记录详情
     */
    @SaCheckPermission("mes:eq:maintenance:record:query")
    @GetMapping("/{recordId}")
    public R<EqMaintenanceRecordVo> getInfo(@PathVariable Long recordId) {
        return R.ok(eqMaintenanceRecordService.selectById(recordId));
    }

    /**
     * 查询某设备的保养执行历史(设备详情页使用)
     */
    @SaCheckPermission("mes:eq:maintenance:record:query")
    @GetMapping("/equipment/{equipmentId}")
    public R<List<EqMaintenanceRecordVo>> listByEquipment(@PathVariable Long equipmentId) {
        return R.ok(eqMaintenanceRecordService.selectByEquipment(equipmentId));
    }

    /**
     * 手动新增保养执行记录(无计划的手动保养)
     */
    @SaCheckPermission("mes:eq:maintenance:complete")
    @Log(title = "手动保养记录", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqMaintenanceRecordVo> add(@Validated @RequestBody EqMaintenanceRecordBo bo) {
        return R.ok(eqMaintenanceRecordService.insertByBo(bo));
    }

    /**
     * 导出保养执行记录列表
     */
    @SaCheckPermission("mes:eq:maintenance:record:export")
    @Log(title = "保养执行记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqMaintenanceRecordBo bo, HttpServletResponse response) {
        List<EqMaintenanceRecordVo> list = eqMaintenanceRecordService.selectList(bo);
        ExcelUtil.exportExcel(list, "保养执行记录", EqMaintenanceRecordVo.class, response);
    }

    /**
     * 批量删除保养执行记录
     */
    @SaCheckPermission("mes:eq:maintenance:record:remove")
    @Log(title = "保养记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqMaintenanceRecordService.deleteByIds(ids));
    }
}
