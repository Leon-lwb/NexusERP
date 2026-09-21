package com.nexus.mes.common.bridge;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
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
 * <p>
 * 租户守卫(MES-MQ-01/MES-BRG-01): 桥接 SQL 为裸 {@code @Select}, 租户隔离完全依赖租户插件注入
 * {@code tenant_id} —— 无租户上下文的线程(异步/定时/MQ)会静默返回空, 表现为"ERP 数据不存在";
 * 系统租户 000000 下查主数据同样必然为空。因此入口断言租户上下文非空, 且系统租户下主数据为空时
 * 显式报错, 避免生成"无单位、无工序"的工单。
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
        assertTenantContext("查询ERP物料");
        try {
            DynamicDataSourceContextHolder.push("erp");
            Map<String, Object> material = erpMaterialMapper.selectMaterialById(materialId);
            failIfSystemTenantEmpty(material == null, "ERP 物料不存在: materialId=" + materialId);
            return material;
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
        assertTenantContext("查询ERP物料完整信息");
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
        assertTenantContext("查询ERP BOM明细");
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
        assertTenantContext("查询ERP BOM头");
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
        assertTenantContext("查询ERP工艺路线头");
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
        assertTenantContext("查询ERP工位");
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
        assertTenantContext("查询ERP工艺路线步骤");
        try {
            DynamicDataSourceContextHolder.push("erp");
            List<Map<String, Object>> steps = erpRoutingStepMapper.selectRoutingStepsByRoutingId(routingId);
            failIfSystemTenantEmpty(steps == null || steps.isEmpty(),
                "ERP 工艺路线无工序明细: routingId=" + routingId);
            return steps == null ? Collections.emptyList() : steps;
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
        assertTenantContext("查询ERP默认工艺路线");
        try {
            DynamicDataSourceContextHolder.push("erp");
            Long routingId = erpMaterialMapper.selectDefaultRoutingId(materialId);
            failIfSystemTenantEmpty(routingId == null, "ERP 未配置物料默认工艺路线: materialId=" + materialId);
            return routingId;
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
        assertTenantContext("查询ERP车间");
        try {
            DynamicDataSourceContextHolder.push("erp");
            Map<String, Object> workshop = erpWorkshopMapper.selectWorkshopById(workshopId);
            failIfSystemTenantEmpty(workshop == null, "ERP 车间不存在: workshopId=" + workshopId);
            return workshop;
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
        assertTenantContext("查询ERP工作站");
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
        assertTenantContext("查询ERP计量单位");
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
        assertTenantContext("查询ERP工作中心列表");
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpWorkstationMapper.selectWorkstationList(keyword);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 断言存在真实租户上下文
     * <p>无租户上下文时桥接查询会被租户插件注入 {@code tenant_id = '000000'}, 结果静默为空 ——
     * 表现为"ERP 主数据不存在"而难以排查(MES-BRG-01), 故此处 fail-fast。</p>
     *
     * @param action 操作描述(用于错误消息)
     */
    private void assertTenantContext(String action) {
        String tenantId;
        try {
            tenantId = TenantHelper.getTenantId();
        } catch (Exception e) {
            throw new ServiceException("MES-ERP 桥接[" + action + "]缺少租户上下文, 拒绝查询 ERP 主数据: "
                + e.getMessage());
        }
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("MES-ERP 桥接[" + action + "]缺少租户上下文, 拒绝查询 ERP 主数据"
                + "(无租户时查询必然为空, 会生成无单位/无工序的工单)");
        }
    }

    /**
     * 系统租户(000000)下主数据为空时显式报错
     * <p>租户为 000000 说明调用方很可能缺失真实租户上下文(典型: MQ 消息未携带 tenantId),
     * 此时 ERP 主数据查询必然为空, 继续执行会静默产出"无单位、无工序"的工单(MES-MQ-01 ③)。</p>
     *
     * @param empty 查询结果是否为空
     * @param desc  可读描述
     */
    private void failIfSystemTenantEmpty(boolean empty, String desc) {
        if (!empty) {
            return;
        }
        String tenantId;
        try {
            tenantId = TenantHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        if ("000000".equals(tenantId)) {
            throw new ServiceException(desc + " (当前租户为系统租户 000000, 通常意味着调用线程缺少真实租户上下文"
                + "或 MQ 消息未携带 tenantId, 请检查生产端)");
        }
    }
}
