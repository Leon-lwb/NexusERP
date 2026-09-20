package com.nexus.erp.wm.service.mq;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MES 备件 → ERP 仓储过账消息（MES-EQ-04）
 * <p>
 * 跨模块消息体定义在消费端模块（{@code ruoyi-erp}）而非 {@code ruoyi-common-mq}：
 * 本次改动的文件所有权只允许在 {@code QueueConstants} 中新增常量，不允许在
 * {@code ruoyi-common-mq} 新增类；生产端（{@code ruoyi-mes} 的
 * {@code EqSpareErpMqPublisher}）以 {@code Map} 承载同名字段，消费端用本类做字段映射。
 * 字段名即消息契约，两端必须保持一致。
 * <p>
 * {@code bizType}：
 * <ul>
 *   <li>{@link #BIZ_STOCK}：备件出入库过账（IN/RETURN 入账、OUT/REPAIR_USE 出账）；</li>
 *   <li>{@link #BIZ_PURCHASE}：低库存一键生成采购申请，请购行见 {@link #lines}。</li>
 * </ul>
 *
 * @author Nexus
 */
@Data
public class MesSpareErpMessage {

    /** 业务类型: 备件出入库过账 */
    public static final String BIZ_STOCK = "SPARE_STOCK";
    /** 业务类型: 低库存生成采购申请 */
    public static final String BIZ_PURCHASE = "SPARE_PURCHASE";

    /** 业务类型 */
    private String bizType;

    /** 租户ID(6位零填充字符串) */
    private String tenantId;

    /** 业务幂等键 EQSP:<tenantId>:<logId> */
    private String bizKey;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    private String spareCode;

    /** 备件名称 */
    private String spareName;

    /** ERP物料ID */
    private Long materialId;

    /** ERP仓库ID */
    private Long warehouseId;

    /** 变动类型 IN/OUT/REPAIR_USE/RETURN */
    private String transactionType;

    /** 变动数量(正数) */
    private BigDecimal quantity;

    /** 关联单据类型 */
    private String refType;

    /** 关联单据ID */
    private Long refId;

    /** 备注 */
    private String remark;

    /** 请购行(BIZ_PURCHASE): materialId/materialCode/materialName/unit/applyQuantity/spareId/spareCode */
    private List<Map<String, Object>> lines = new ArrayList<>();

    /**
     * 由消息 Map 构造（生产端以 Map 承载同名字段）
     *
     * @param map 消息体
     * @return 消息对象；map 为 null 时返回 null
     */
    @SuppressWarnings("unchecked")
    public static MesSpareErpMessage fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        MesSpareErpMessage msg = new MesSpareErpMessage();
        msg.setBizType(str(map.get("bizType")));
        msg.setTenantId(str(map.get("tenantId")));
        msg.setBizKey(str(map.get("bizKey")));
        msg.setSpareId(longVal(map.get("spareId")));
        msg.setSpareCode(str(map.get("spareCode")));
        msg.setSpareName(str(map.get("spareName")));
        msg.setMaterialId(longVal(map.get("materialId")));
        msg.setWarehouseId(longVal(map.get("warehouseId")));
        msg.setTransactionType(str(map.get("transactionType")));
        msg.setQuantity(decimal(map.get("quantity")));
        msg.setRefType(str(map.get("refType")));
        msg.setRefId(longVal(map.get("refId")));
        msg.setRemark(str(map.get("remark")));
        Object lines = map.get("lines");
        if (lines instanceof List<?> list) {
            List<Map<String, Object>> parsed = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    parsed.add((Map<String, Object>) m);
                }
            }
            msg.setLines(parsed);
        } else {
            msg.setLines(Collections.emptyList());
        }
        return msg;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
