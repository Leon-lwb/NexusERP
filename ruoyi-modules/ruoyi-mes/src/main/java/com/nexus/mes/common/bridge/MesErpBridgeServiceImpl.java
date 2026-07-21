package com.nexus.mes.common.bridge;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.mes.common.mapper.ErpMaterialMapper;
import com.nexus.mes.common.mapper.ErpBomDetailMapper;
import com.nexus.mes.common.mapper.ErpBomHeaderMapper;
import com.nexus.mes.common.mapper.ErpRoutingHeaderMapper;
import com.nexus.mes.common.mapper.ErpRoutingStepMapper;
import com.nexus.mes.common.mapper.ErpWorkshopMapper;
import com.nexus.mes.common.mapper.ErpWorkstationMapper;
import com.nexus.mes.common.mapper.ErpUnitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MES-ERP桥接服务实现
 * <p>
 * 使用DynamicDataSourceContextHolder编程式切换到erp数据源，
 * 避免与MES的@DS("mes")注解冲突。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MesErpBridgeServiceImpl implements IMesErpBridgeService {

    private final ErpMaterialMapper erpMaterialMapper;
    private final ErpBomDetailMapper erpBomDetailMapper;
    private final ErpBomHeaderMapper erpBomHeaderMapper;
    private final ErpRoutingHeaderMapper erpRoutingHeaderMapper;
    private final ErpRoutingStepMapper erpRoutingStepMapper;
    private final ErpWorkshopMapper erpWorkshopMapper;
    private final ErpWorkstationMapper erpWorkstationMapper;
    private final ErpUnitMapper erpUnitMapper;

    @Override
    /**
     * 获取
     * @param materialId 物料ID
     * @return 返回结果映射
     */
    public Map<String, Object> getMaterial(Long materialId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpMaterialMapper.selectMaterialById(materialId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param materialId 物料ID
     * @return 返回结果映射
     */
    public Map<String, Object> getMaterialFull(Long materialId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpMaterialMapper.selectMaterialFullById(materialId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param bomId BOMID
     * @return 结果列表
     */
    public List<Map<String, Object>> getBomDetails(Long bomId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpBomDetailMapper.selectBomDetailsByBomId(bomId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param bomId BOMID
     * @return 返回结果映射
     */
    public Map<String, Object> getBomHeader(Long bomId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpBomHeaderMapper.selectBomHeaderById(bomId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param routingId 工艺路线ID
     * @return 返回结果映射
     */
    public Map<String, Object> getRoutingHeader(Long routingId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpRoutingHeaderMapper.selectRoutingHeaderById(routingId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param workstationId 工单rkstationId
     * @return 返回结果映射
     */
    public Map<String, Object> getWorkstationFull(Long workstationId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkstationMapper.selectWorkstationById(workstationId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param routingId 工艺路线ID
     * @return 结果列表
     */
    public List<Map<String, Object>> getRoutingSteps(Long routingId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpRoutingStepMapper.selectRoutingStepsByRoutingId(routingId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param materialId 物料ID
     * @return 受影响行数
     */
    public Long getDefaultRoutingId(Long materialId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpMaterialMapper.selectDefaultRoutingId(materialId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param workshopId 工单rkshopId
     * @return 返回结果映射
     */
    public Map<String, Object> getWorkshop(Long workshopId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkshopMapper.selectWorkshopById(workshopId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param workstationId 工单rkstationId
     * @return 返回结果映射
     */
    public Map<String, Object> getWorkstation(Long workstationId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkstationMapper.selectWorkstationById(workstationId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 获取
     * @param unitId 单位ID
     * @return 返回结果
     */
    public String getUnitName(Long unitId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpUnitMapper.selectUnitNameById(unitId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    /**
     * 分页查询列表
     * @param keyword keyword
     * @return 结果列表
     */
    public List<Map<String, Object>> listWorkstations(String keyword) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkstationMapper.selectWorkstationList(keyword);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
