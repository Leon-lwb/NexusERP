package com.nexus.erp.wm.trace;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.port.MaterialTrackingUsagePort;
import com.nexus.erp.bd.domain.BdMaterial;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Objects;

/**
 * 主数据追踪策略只能在无遗留使用或完成身份转换后变更。
 */
@Service
@RequiredArgsConstructor
public class MaterialTrackingPolicyGuard {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;
    private final ObjectProvider<MaterialTrackingUsagePort> mesUsage;

    @Value("${nexus.trace.relay.enabled:false}")
    private boolean relayEnabled;

    public void prepare(BdMaterial old, BdMaterial next, Long expectedVersion) {
        long version = old.getTrackingVersion() == null ? 1 : old.getTrackingVersion();
        Long oldBatch = old.getIsBatchManage() == null ? 0L : old.getIsBatchManage(),
                oldSerial = old.getIsSerialManage() == null ? 0L : old.getIsSerialManage();
        next.setIsBatchManage(next.getIsBatchManage() == null ? oldBatch : next.getIsBatchManage());
        next.setIsSerialManage(
                next.getIsSerialManage() == null ? oldSerial : next.getIsSerialManage());
        next.setAllowMixedLot(
                next.getAllowMixedLot() == null
                        ? (old.getAllowMixedLot() == null ? 1 : old.getAllowMixedLot())
                        : next.getAllowMixedLot());
        next.setTrackingStage(
                next.getTrackingStage() == null
                        ? (old.getTrackingStage() == null ? "AUDIT" : old.getTrackingStage())
                        : next.getTrackingStage());
        flag(next.getIsBatchManage());
        flag(next.getIsSerialManage());
        if (next.getAllowMixedLot() != 0 && next.getAllowMixedLot() != 1
                || !java.util.Set.of("AUDIT", "STRICT").contains(next.getTrackingStage())) {
            throw new ServiceException("追踪阶段或混批配置无效");
        }
        boolean dimensions =
                !Objects.equals(next.getIsBatchManage(), oldBatch)
                        || !Objects.equals(next.getIsSerialManage(), oldSerial)
                        || !Objects.equals(
                                next.getAllowMixedLot(),
                                old.getAllowMixedLot() == null ? 1 : old.getAllowMixedLot());
        boolean stage =
                !Objects.equals(
                        next.getTrackingStage(),
                        old.getTrackingStage() == null ? "AUDIT" : old.getTrackingStage());
        if (!dimensions && !stage) {
            next.setTrackingVersion(version);
            return;
        }
        String tenant = context.tenant();
        context.requireWrite(tenant);
        if (expectedVersion == null || expectedVersion != version) {
            throw new ServiceException("追踪策略版本已变化，请刷新物料后操作");
        }
        var usage = mesUsage.getIfAvailable();
        if (dimensions) {
            long stock =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id=? AND"
                                    + " material_id=? AND del_flag='0' AND"
                                    + " (COALESCE(stock_quantity,0)<>0 OR"
                                    + " COALESCE(reserved_quantity,0)<>0)",
                            Long.class,
                            tenant,
                            old.getMaterialId());
            long pending =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM wm_serial_reservation r WHERE r.tenant_id=? AND"
                                + " r.material_id=? AND r.status='RESERVED' AND EXISTS (SELECT 1"
                                + " FROM wm_sn n WHERE n.tenant_id=r.tenant_id AND"
                                + " n.registration_command=r.command_id AND n.status='REGISTERED'"
                                + " AND n.del_flag='0')",
                            Long.class,
                            tenant,
                            old.getMaterialId());
            if (stock > 0
                    || pending > 0
                    || usage == null
                    || usage.hasUnsettledProduction(tenant, old.getMaterialId())) {
                throw new ServiceException("存在库存、身份预留或在制使用，须通过身份转换流程修改策略");
            }
        }
        if (stage && "STRICT".equals(next.getTrackingStage())) {
            if (!relayEnabled || usage == null) {
                throw new ServiceException("跨模块同步或MES就绪检查未启用，不能切换严格模式");
            }
            if (Long.valueOf(1).equals(next.getIsSerialManage())) {
                long invalidBalances =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id=? AND"
                                    + " material_id=? AND del_flag='0' AND (stock_quantity IS NULL"
                                    + " OR stock_quantity<0 OR reserved_quantity IS NULL OR"
                                    + " reserved_quantity<0 OR reserved_quantity>stock_quantity OR"
                                    + " (stock_quantity>0 AND (total_cost IS NULL OR total_cost<0))"
                                    + " OR (stock_quantity=0 AND COALESCE(total_cost,0)<>0))",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                if (invalidBalances > 0) {
                    throw new ServiceException("当前库存数量、预留或金额仍有未对账事实");
                }
                long invalid =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_material_stock s LEFT JOIN wm_sn n ON"
                                    + " n.tenant_id=s.tenant_id AND n.sn_id=s.sn_id AND"
                                    + " n.del_flag='0' WHERE s.tenant_id=? AND s.material_id=? AND"
                                    + " s.del_flag='0' AND COALESCE(s.stock_quantity,0)>0 AND"
                                    + " (s.stock_quantity<>1 OR s.reserved_quantity NOT IN (0,1) OR"
                                    + " s.sn_id IS NULL OR n.sn_id IS NULL OR n.material_id IS NULL"
                                    + " OR n.material_id<>s.material_id OR s.sn_code IS NULL OR"
                                    + " TRIM(s.sn_code)='' OR n.sn_code IS NULL OR CAST(n.sn_code"
                                    + " AS BINARY(512))<>CAST(s.sn_code AS BINARY(512)) OR"
                                    + " COALESCE(n.batch_id,0)<>COALESCE(s.batch_id,0) OR"
                                    + " CAST(COALESCE(n.batch_code,'') AS"
                                    + " BINARY(512))<>CAST(COALESCE(s.batch_code,'') AS"
                                    + " BINARY(512)) OR n.stock_id<>s.stock_id OR n.stock_id IS"
                                    + " NULL OR n.status<>'IN_STOCK' OR n.status IS NULL OR"
                                    + " s.warehouse_id IS NULL OR s.warehouse_id<=0 OR"
                                    + " (s.location_id IS NOT NULL AND s.location_id<=0) OR"
                                    + " (s.batch_id IS NOT NULL AND s.batch_id<=0) OR"
                                    + " COALESCE(n.warehouse_id,0)<>COALESCE(s.warehouse_id,0) OR"
                                    + " COALESCE(n.location_id,0)<>COALESCE(s.location_id,0) OR"
                                    + " s.tracking_version<2)",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                long duplicates =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM (SELECT sn_id FROM wm_material_stock WHERE"
                                        + " tenant_id=? AND material_id=? AND del_flag='0' AND"
                                        + " stock_quantity>0 GROUP BY sn_id HAVING COUNT(*)>1)"
                                        + " duplicate_sn",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                long orphanIdentities =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_sn n WHERE n.tenant_id=? AND"
                                    + " n.material_id=? AND n.del_flag='0' AND n.status='IN_STOCK'"
                                    + " AND NOT EXISTS (SELECT 1 FROM wm_material_stock s WHERE"
                                    + " s.tenant_id=n.tenant_id AND s.material_id=n.material_id AND"
                                    + " s.sn_id=n.sn_id AND s.del_flag='0' AND s.stock_quantity>0)",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                if (invalid > 0 || duplicates > 0 || orphanIdentities > 0) {
                    throw new ServiceException("在库SN尚未完成唯一身份与数量对账");
                }
                long invalidGroups =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_material_stock s LEFT JOIN"
                                    + " wm_valuation_group g ON g.tenant_id=s.tenant_id AND"
                                    + " g.valuation_group_id=s.valuation_group_id WHERE"
                                    + " s.tenant_id=? AND s.material_id=? AND s.del_flag='0' AND"
                                    + " s.stock_quantity>0 AND (g.valuation_group_id IS NULL OR"
                                    + " g.material_id IS NULL OR g.material_id<>s.material_id OR"
                                    + " COALESCE(g.warehouse_id,0)<>COALESCE(s.warehouse_id,0) OR"
                                    + " COALESCE(g.batch_id,0)<>COALESCE(s.batch_id,0))",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                long unbalancedGroups =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM (SELECT"
                                    + " valuation_group_id,SUM(COALESCE(stock_quantity,0))"
                                    + " quantity,SUM(COALESCE(total_cost,0)) amount FROM"
                                    + " wm_material_stock WHERE tenant_id=? AND material_id=? AND"
                                    + " del_flag='0' AND valuation_group_id IS NOT NULL GROUP BY"
                                    + " valuation_group_id) balances LEFT JOIN wm_valuation_group g"
                                    + " ON g.tenant_id=? AND"
                                    + " g.valuation_group_id=balances.valuation_group_id WHERE"
                                    + " g.valuation_group_id IS NULL OR g.quantity IS NULL OR"
                                    + " g.amount IS NULL OR g.quantity<>balances.quantity OR"
                                    + " g.amount<>balances.amount",
                                Long.class,
                                tenant,
                                old.getMaterialId(),
                                tenant);
                long orphanGroups =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_valuation_group g LEFT JOIN (SELECT"
                                    + " valuation_group_id,SUM(COALESCE(stock_quantity,0))"
                                    + " quantity,SUM(COALESCE(total_cost,0)) amount FROM"
                                    + " wm_material_stock WHERE tenant_id=? AND material_id=? AND"
                                    + " del_flag='0' AND valuation_group_id IS NOT NULL GROUP BY"
                                    + " valuation_group_id) balances ON"
                                    + " balances.valuation_group_id=g.valuation_group_id WHERE"
                                    + " g.tenant_id=? AND g.material_id=? AND (g.quantity IS NULL"
                                    + " OR g.amount IS NULL OR g.quantity<0 OR g.amount<0 OR"
                                    + " g.quantity<>COALESCE(balances.quantity,0) OR"
                                    + " g.amount<>COALESCE(balances.amount,0))",
                                Long.class,
                                tenant,
                                old.getMaterialId(),
                                tenant,
                                old.getMaterialId());
                if (invalidGroups > 0 || unbalancedGroups > 0 || orphanGroups > 0) {
                    throw new ServiceException("原库存计价范围、数量或金额尚未完成对账");
                }
            }
            if (Long.valueOf(1).equals(next.getIsBatchManage())) {
                long invalid =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wm_material_stock s LEFT JOIN wm_batch b ON"
                                    + " b.tenant_id=s.tenant_id AND b.batch_id=s.batch_id AND"
                                    + " b.del_flag='0' WHERE s.tenant_id=? AND s.material_id=? AND"
                                    + " s.del_flag='0' AND s.stock_quantity>0 AND (b.batch_id IS"
                                    + " NULL OR b.material_id IS NULL OR"
                                    + " b.material_id<>s.material_id OR b.batch_code IS NULL OR"
                                    + " CAST(b.batch_code AS BINARY(512))<>CAST(s.batch_code AS"
                                    + " BINARY(512)) OR s.batch_code IS NULL OR"
                                    + " TRIM(s.batch_code)='')",
                                Long.class,
                                tenant,
                                old.getMaterialId());
                if (invalid > 0) {
                    throw new ServiceException("在库批次尚未完成来源与物料对账");
                }
            }
            usage.assertReadyForStrict(tenant, old.getMaterialId());
        }
        if (stage
                && "AUDIT".equals(next.getTrackingStage())
                && "STRICT".equals(old.getTrackingStage())) {
            throw new ServiceException("严格模式已有独立事实，回退须走兼容切换流程");
        }
        next.setTrackingVersion(Math.addExact(version, 1));
    }

    private static void flag(Long value) {
        if (value == null || value != 0L && value != 1L) {
            throw new ServiceException("物料追踪开关必须为0或1");
        }
    }
}
