package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.TrackingPolicy;
import com.nexus.common.trace.port.MaterialPolicyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * ERP物料策略的可信只读入口；未迁移旧库按审计模式解释，不在读取时改库。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpMaterialPolicyService implements MaterialPolicyPort {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TrackingPolicy policy(String tenantId, Long materialId) {
        context.requireTenant(tenantId);
        var rows =
                jdbc.queryForList(
                        "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND"
                                + " del_flag='0'",
                        tenantId,
                        materialId);
        if (rows.size() != 1) {
            throw new ServiceException("物料不存在或不在当前租户范围");
        }
        return fromRow(rows.get(0));
    }

    static TrackingPolicy fromRow(Map<String, Object> row) {
        return TrackingPolicy.of(
                number(row.get("is_batch_manage")),
                number(row.get("is_serial_manage")),
                row.get("allow_mixed_lot") == null || number(row.get("allow_mixed_lot")) == 1L,
                row.get("tracking_version") == null ? 1L : number(row.get("tracking_version")),
                row.get("tracking_stage") == null ? "AUDIT" : row.get("tracking_stage").toString());
    }

    static Long number(Object value) {
        return value == null
                ? null
                : value instanceof Boolean b ? (b ? 1L : 0L) : ((Number) value).longValue();
    }
}
