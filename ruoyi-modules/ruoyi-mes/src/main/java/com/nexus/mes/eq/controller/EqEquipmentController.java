package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.common.bridge.IMesErpBridgeService;
import com.nexus.mes.eq.service.IEqEquipmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.excel.utils.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MES设备台账 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/equipment")
public class EqEquipmentController extends BaseController {

    private final IEqEquipmentService eqEquipmentService;
    private final IMesErpBridgeService mesErpBridgeService;

    /**
     * 分页查询设备台账列表
     */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/list")
    public TableDataInfo<EqEquipmentVo> list(EqEquipmentBo bo, PageQuery pageQuery) {
        return eqEquipmentService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取设备详情
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}")
    public R<EqEquipmentVo> getInfo(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.selectById(equipmentId));
    }

    /**
     * 新增设备
     */
    @SaCheckPermission("mes:eq:equipment:add")
    @Log(title = "设备新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqEquipmentVo> add(@Validated @RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.insertByBo(bo));
    }

    /**
     * 编辑设备
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @Log(title = "设备编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<EqEquipmentVo> edit(@Validated @RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.updateByBo(bo));
    }

    /**
     * 批量删除设备
     */
    @SaCheckPermission("mes:eq:equipment:remove")
    @Log(title = "设备删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqEquipmentService.deleteByIds(ids));
    }

    /**
     * 查询设备关联的 IoT 在线状态
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotStatus")
    public R<String> getIotStatus(@PathVariable Long equipmentId) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把状态误塞进 msg
        return R.ok("查询成功", eqEquipmentService.getIotStatus(equipmentId));
    }

    /**
     * 查询设备关联的 IoT 最新数据
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotLatest")
    public R<Map<String, String>> getIotLatest(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.getIotLatest(equipmentId));
    }

    /**
     * 查询设备当前关联的进行中工单(P1-6 设备-工单联动)
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/currentWorkOrder")
    public R<List<ProWorkOrderVo>> currentWorkOrder(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.currentWorkOrders(equipmentId));
    }

    /**
     * 设备状态计数汇总(监控大屏用, 全量 COUNT 不受分页限制)
     */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(eqEquipmentService.statusSummary());
    }

    /**
     * 更新设备关联的 IoT 设备
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @Log(title = "设备关联IoT", businessType = BusinessType.UPDATE)
    @PutMapping("/{equipmentId}/iotLink")
    public R<Void> updateIotLink(@PathVariable Long equipmentId,
                                 @RequestBody IotLinkDto dto) {
        return toAjax(eqEquipmentService.updateIotDeviceLink(equipmentId, dto.getIotDeviceId()));
    }

    /**
     * 提交设备点检结果
     */
    @SaCheckPermission("mes:eq:equipment:inspect")
    @Log(title = "设备点检", businessType = BusinessType.UPDATE)
    @PostMapping("/{equipmentId}/inspect")
    public R<Void> submitInspection(@PathVariable Long equipmentId,
                                    @Validated @RequestBody InspectionResultBo bo) {
        return toAjax(eqEquipmentService.submitInspection(equipmentId, bo));
    }

    /**
     * 批量查询设备 IoT 在线状态(消除前端逐行 N+1 HTTP 请求)
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/batchIotStatus")
    public R<Map<Long, String>> batchIotStatus(@RequestParam String equipmentIds) {
        List<Long> ids = Arrays.stream(equipmentIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::valueOf)
            .collect(Collectors.toList());
        return R.ok(eqEquipmentService.batchIotStatus(ids));
    }

    /**
     * 查询 ERP 工作中心列表(供设备表单工作中心选择器使用)
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/workcenters")
    public R<List<Map<String, Object>>> listWorkcenters(@RequestParam(required = false) String keyword) {
        return R.ok(mesErpBridgeService.listWorkstations(keyword));
    }

    /**
     * 导出设备台账数据
     */
    @SaCheckPermission("mes:eq:equipment:export")
    @Log(title = "设备台账", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqEquipmentBo bo, HttpServletResponse response) {
        List<EqEquipmentVo> list = eqEquipmentService.selectList(bo);
        ExcelUtil.exportExcel(list, "设备台账", EqEquipmentVo.class, response);
    }

    @Data
    public static class IotLinkDto {
        private Long iotDeviceId;
    }
}
