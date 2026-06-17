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
     * 查询BOM明细(从ERP)
     *
     * @param bomId BOM ID
     * @return BOM行列表，每行包含child_material_id/quantity_per_parent等
     */
    List<Map<String, Object>> getBomLines(Long bomId);

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
}
