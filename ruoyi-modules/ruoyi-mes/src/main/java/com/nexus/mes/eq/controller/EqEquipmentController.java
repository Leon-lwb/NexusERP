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
 * <p>属于MES 设备管理模块，提供设备相关的 REST 接口。</p>
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
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:equipment:list")
    @GetMapping("/list")
    public TableDataInfo<EqEquipmentVo> list(EqEquipmentBo bo, PageQuery pageQuery) {
        return eqEquipmentService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取设备详情
     *
     * @param equipmentId 设备ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}")
    public R<EqEquipmentVo> getInfo(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.selectById(equipmentId));
    }

    /**
     * 新增设备
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:add")
    @Log(title = "设备新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqEquipmentVo> add(@Validated @RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.insertByBo(bo));
    }

    /**
     * 编辑设备
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @Log(title = "设备编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<EqEquipmentVo> edit(@Validated @RequestBody EqEquipmentBo bo) {
        return R.ok(eqEquipmentService.updateByBo(bo));
    }

    /**
     * 批量删除设备
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:remove")
    @Log(title = "设备删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqEquipmentService.deleteByIds(ids));
    }

    /**
     * 查询设备关联的 IoT 在线状态
     *
     * @param equipmentId 设备ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotStatus")
    public R<String> getIotStatus(@PathVariable Long equipmentId) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把状态误塞进 msg
        return R.ok("查询成功", eqEquipmentService.getIotStatus(equipmentId));
    }

    /**
     * 查询设备关联的 IoT 最新数据
     *
     * @param equipmentId 设备ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/iotLatest")
    public R<Map<String, String>> getIotLatest(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.getIotLatest(equipmentId));
    }

    /**
     * 查询设备当前关联的进行中工单(P1-6 设备-工单联动)
     *
     * @param equipmentId 设备ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/{equipmentId}/currentWorkOrder")
    public R<List<ProWorkOrderVo>> currentWorkOrder(@PathVariable Long equipmentId) {
        return R.ok(eqEquipmentService.currentWorkOrders(equipmentId));
    }

    /**
     * 设备状态计数汇总(监控大屏用, 全量 COUNT 不受分页限制)
     *
     * @return 操作结果
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
     * 结束保养: 设备由 MAINTENANCE 按既有状态机回到 NORMAL(仍有活跃报修则 FAULT) (MES-EQ-05)
     *
     * @param equipmentId 设备ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:edit")
    @Log(title = "设备结束保养", businessType = BusinessType.UPDATE)
    @PutMapping("/{equipmentId}/finishMaintenance")
    public R<Void> finishMaintenance(@PathVariable Long equipmentId,
                                     @RequestParam(required = false) String reason) {
        return toAjax(eqEquipmentService.finishMaintenance(equipmentId, reason));
    }

    /**
     * 批量查询设备 IoT 在线状态(消除前端逐行 N+1 HTTP 请求)
     *
     * @param equipmentIds 设备ID列表（逗号分隔）
     * @return 操作结果
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
     *
     * @param keyword 模糊匹配关键字（可空）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:equipment:query")
    @GetMapping("/workcenters")
    public R<List<Map<String, Object>>> listWorkcenters(@RequestParam(required = false) String keyword) {
        return R.ok(mesErpBridgeService.listWorkstations(keyword));
    }

    /**
     * 导出设备台账数据
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("mes:eq:equipment:export")
    @Log(title = "设备台账", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqEquipmentBo bo, HttpServletResponse response) {
        List<EqEquipmentVo> list = eqEquipmentService.selectList(bo);
        ExcelUtil.exportExcel(list, "设备台账", EqEquipmentVo.class, response);
    }

    /**
     * 属于MES 设备管理模块，IoTLinkDto持久化实体。
     */
    @Data
    public static class IotLinkDto {
        private Long iotDeviceId;
    }
}
