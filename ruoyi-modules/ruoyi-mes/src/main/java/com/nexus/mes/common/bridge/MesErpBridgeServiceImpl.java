package com.nexus.mes.common.bridge;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.mes.common.mapper.ErpMaterialMapper;
import com.nexus.mes.common.mapper.ErpBomLineMapper;
import com.nexus.mes.common.mapper.ErpRoutingStepMapper;
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
    private final ErpBomLineMapper erpBomLineMapper;
    private final ErpRoutingStepMapper erpRoutingStepMapper;

    @Override
    public Map<String, Object> getMaterial(Long materialId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpMaterialMapper.selectMaterialById(materialId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    public List<Map<String, Object>> getBomLines(Long bomId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpBomLineMapper.selectBomLinesByBomId(bomId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    public List<Map<String, Object>> getRoutingSteps(Long routingId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpRoutingStepMapper.selectRoutingStepsByRoutingId(routingId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    @Override
    public Long getDefaultRoutingId(Long materialId) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpMaterialMapper.selectDefaultRoutingId(materialId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
