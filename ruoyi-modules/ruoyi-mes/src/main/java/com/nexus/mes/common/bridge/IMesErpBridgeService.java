package com.nexus.mes.common.bridge;

import java.util.List;
import java.util.Map;

/**
 * MES-ERP桥接服务，封装跨库查询逻辑
 * <p>
 * MES Service通过DynamicDataSourceContextHolder临时切换到erp数据源读取ERP主数据，
 * 避免MES模块直接依赖ruoyi-erp模块。
 *
 * @author Nexus
 */
public interface IMesErpBridgeService {

    /**
     * 查询物料信息(从ERP)
     *
     * @param materialId 物料ID
     * @return 物料信息Map，包含material_code/material_name/material_spec等
     */
    Map<String, Object> getMaterial(Long materialId);

    /**
     * 查询物料完整信息(从ERP,含标准成本/基本单位)
     *
     * @param materialId 物料ID
     * @return 物料信息Map，包含material_code/material_name/material_spec/standard_cost/base_uom_id/base_uom_name等
     */
    Map<String, Object> getMaterialFull(Long materialId);

    /**
     * 查询BOM明细(从ERP,纯物料+用量)
     *
     * @param bomId BOM ID
     * @return BOM行列表，每行包含detail_id/child_material_id/quantity_per_parent/loss_rate/operation_step等
     */
    List<Map<String, Object>> getBomDetails(Long bomId);

    /**
     * 查询BOM头信息(从ERP,含父项物料编码/名称/版本/路线)
     *
     * @param bomId BOM ID
     * @return BOM头信息Map
     */
    Map<String, Object> getBomHeader(Long bomId);

    /**
     * 查询工艺路线头信息(从ERP,含路线编码/名称/版本/物料编码/名称)
     *
     * @param routingId 工艺路线ID
     * @return 路线头信息Map
     */
    Map<String, Object> getRoutingHeader(Long routingId);

    /**
     * 查询工位完整信息(从ERP)
     *
     * @param workstationId 工位ID
     * @return 工位信息Map，包含workstation_code/workstation_name等
     */
    Map<String, Object> getWorkstationFull(Long workstationId);

    /**
     * 查询工艺路线步骤(从ERP)
     *
     * @param routingId 工艺路线ID
     * @return 工艺步骤列表，按sequence_num排序，每行包含process_id/process_code/process_name等
     */
    List<Map<String, Object>> getRoutingSteps(Long routingId);

    /**
     * 根据物料ID查询默认工艺路线ID(从ERP)
     * <p>
     * bd_material表无default_routing_id字段，通过bd_routing表查询:
     * material_id匹配且is_current=1且status=1的当前版本工艺路线
     *
     * @param materialId 物料ID
     * @return 工艺路线ID，可能为null
     */
    Long getDefaultRoutingId(Long materialId);

    /**
     * 查询车间信息(从ERP)
     *
     * @param workshopId 车间ID
     * @return 车间信息Map，包含workshop_code/workshop_name等
     */
    Map<String, Object> getWorkshop(Long workshopId);

    /**
     * 查询工作站信息(从ERP)
     *
     * @param workstationId 工作站ID
     * @return 工作站信息Map，包含workstation_code/workstation_name等
     */
    Map<String, Object> getWorkstation(Long workstationId);

    /**
     * 查询计量单位名称(从ERP)
     *
     * @param unitId 单位ID
     * @return 单位名称，可能为null
     */
    String getUnitName(Long unitId);

    /**
     * 查询工作中心列表(从ERP, 供设备表单选择器使用)
     *
     * @param keyword 编码/名称关键字(可空)
     * @return 工作中心列表, 每项含 workstation_id/workstation_code/workstation_name 等
     */
    List<Map<String, Object>> listWorkstations(String keyword);
}
