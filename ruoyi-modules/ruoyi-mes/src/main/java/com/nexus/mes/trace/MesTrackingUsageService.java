package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.port.MaterialTrackingUsagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.nexus.mes.trace.MesTraceContext.need;

/**
 * Fail-closed readiness. A missing migration is not evidence of unused material.
 * Tenant column joins use BINARY(80): twenty utf8mb4 characters, independent of legacy table collations.
 */
@Service @DS("mes") @RequiredArgsConstructor
public class MesTrackingUsageService implements MaterialTrackingUsagePort {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public boolean hasUnsettledProduction(String tenant,Long materialId) {
        context.requireTenant(tenant);need(materialId!=null && materialId>0,"物料身份无效");
        try {
            return count("SELECT COUNT(*) FROM pro_work_order w WHERE w.tenant_id=? AND w.del_flag='0' AND w.wo_status IN ('CREATED','APPROVING','RELEASED','IN_PROGRESS','PARTIAL_COMPLETED') AND (w.material_id=? OR EXISTS(SELECT 1 FROM pro_work_order_bom_line b WHERE CAST(b.tenant_id AS BINARY(80))=CAST(w.tenant_id AS BINARY(80)) AND b.mes_wo_id=w.mes_wo_id AND b.child_material_id=? AND b.del_flag='0'))",tenant,materialId,materialId)>0
                || count("""
                    SELECT COUNT(*) FROM pro_completion_manifest m WHERE m.tenant_id=?
                      AND (m.material_id=? OR EXISTS(SELECT 1 FROM pro_completion_material_allocation a
                          WHERE CAST(a.tenant_id AS BINARY(80))=CAST(m.tenant_id AS BINARY(80))
                            AND a.manifest_id=m.manifest_id AND a.revision=m.revision AND a.material_id=?))
                      AND (m.status IS NULL OR m.status NOT IN ('APPLIED','RECEIVED'))
                      AND NOT EXISTS(SELECT 1 FROM pro_completion_manifest newer
                          WHERE CAST(newer.tenant_id AS BINARY(80))=CAST(m.tenant_id AS BINARY(80))
                            AND newer.mes_wo_id=m.mes_wo_id AND newer.revision>m.revision)
                    """,tenant,materialId,materialId)>0
                || count("""
                    SELECT COUNT(*) FROM pro_sn s WHERE s.tenant_id=? AND s.material_id=? AND s.del_flag='0'
                    AND (s.sn_status='IN_PRODUCTION' OR (s.handoff_status='FROZEN' AND (
                        s.sn_status IS NULL OR s.sn_status<>'SCRAPPED'
                        OR NOT EXISTS (
                            SELECT 1 FROM pro_completion_manifest_line l
                            JOIN pro_completion_manifest m ON CAST(m.tenant_id AS BINARY(80))=CAST(l.tenant_id AS BINARY(80)) AND m.manifest_id=l.manifest_id AND m.revision=l.revision
                            JOIN mes_trace_outbox o ON CAST(o.tenant_id AS BINARY(80))=CAST(m.tenant_id AS BINARY(80)) AND o.aggregate_type='COMPLETION_MANIFEST'
                                AND o.aggregate_id=m.manifest_id AND o.event_type='COMPLETION_MANIFEST_FROZEN' AND o.status='APPLIED'
                            WHERE CAST(l.tenant_id AS BINARY(80))=CAST(s.tenant_id AS BINARY(80)) AND l.local_sn_id=s.sn_id AND l.identity_id=s.identity_id
                                AND l.material_id=s.material_id AND m.mes_wo_id=s.mes_wo_id AND l.result='SCRAP'
                                AND m.status IN ('APPLIED','RECEIVED')
                        )
                        OR EXISTS (
                            SELECT 1 FROM pro_completion_manifest_line l
                            JOIN pro_completion_manifest m ON CAST(m.tenant_id AS BINARY(80))=CAST(l.tenant_id AS BINARY(80)) AND m.manifest_id=l.manifest_id AND m.revision=l.revision
                            WHERE CAST(l.tenant_id AS BINARY(80))=CAST(s.tenant_id AS BINARY(80)) AND l.local_sn_id=s.sn_id AND l.identity_id=s.identity_id
                                AND (m.status IS NULL OR m.status NOT IN ('APPLIED','RECEIVED'))
                        )
                    )))
                    """,tenant,materialId)>0;
        } catch(org.springframework.dao.DataAccessException e){throw new ServiceException("MES追溯结构未就绪，无法确认物料在制情况");}
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public void assertReadyForStrict(String tenant,Long materialId) {
        context.requireTenant(tenant);need(materialId!=null && materialId>0,"物料身份无效");
        try {
            need(count("SELECT COUNT(*) FROM pro_sn WHERE tenant_id=? AND material_id=? AND (identity_id IS NULL OR identity_id<=0 OR identity_version<1)",tenant,materialId)==0,"历史MES身份尚未有证据映射到ERP正式身份");
            need(count("SELECT COUNT(*) FROM (SELECT identity_id FROM pro_sn WHERE tenant_id=? AND material_id=? AND identity_id IS NOT NULL GROUP BY identity_id HAVING COUNT(*)>1) duplicate_identity",tenant,materialId)==0,"MES存在重复正式身份");
            need(count("SELECT COUNT(*) FROM lt_wo_consumption c LEFT JOIN lt_lot_allocation a ON CAST(a.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND a.allocation_id=c.source_allocation_id WHERE c.tenant_id=? AND c.material_id=? AND (a.allocation_id IS NULL OR a.lot_id<>c.lot_id OR a.material_id<>c.material_id OR c.del_flag<>'0')",tenant,materialId)==0,"历史实耗缺少可信来源分配或存在删除审计缺口");
            need(count("SELECT COUNT(*) FROM lt_lot l WHERE l.tenant_id=? AND l.material_id=? AND l.del_flag='0' AND l.quantity>0 AND NOT EXISTS(SELECT 1 FROM lt_lot_allocation a WHERE CAST(a.tenant_id AS BINARY(80))=CAST(l.tenant_id AS BINARY(80)) AND a.lot_id=l.lot_id AND a.material_id=l.material_id)",tenant,materialId)==0,"历史MES批次尚无有据ERP批次数量映射");
            need(count("SELECT COUNT(*) FROM lt_sn_binding b JOIN pro_sn s ON CAST(s.tenant_id AS BINARY(80))=CAST(b.tenant_id AS BINARY(80)) AND s.sn_id=b.sn_id WHERE b.tenant_id=? AND b.material_id=? AND b.material_id<>s.material_id AND NOT EXISTS(SELECT 1 FROM lt_sn_consumption c WHERE CAST(c.tenant_id AS BINARY(80))=CAST(b.tenant_id AS BINARY(80)) AND c.local_sn_id=b.sn_id AND c.component_material_id=b.material_id AND c.component_lot_id=b.lot_id)",tenant,materialId)==0,"历史组件绑定尚未审计为实际数量消耗账");
        } catch(org.springframework.dao.DataAccessException e){throw new ServiceException("MES追溯结构未就绪，禁止切换严格策略");}
    }
    private long count(String sql,Object... args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
}
