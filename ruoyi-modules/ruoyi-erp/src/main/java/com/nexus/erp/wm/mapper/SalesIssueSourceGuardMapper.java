package com.nexus.erp.wm.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 销售出库专属来源锁；显式限制 ERP 数据源、租户、原订单与有效记录。 */
@Mapper
@DS("erp")
public interface SalesIssueSourceGuardMapper {

    /** 在调用方原事务内先锁订单头，同一订单的不同出库单在此串行。 */
    @Select("SELECT so_id AS soId, tenant_id AS tenantId, client_id AS clientId, " +
        "order_status AS orderStatus, del_flag AS delFlag FROM so_sales_order_header " +
        "WHERE tenant_id = #{tenantId} AND so_id = #{soId} AND del_flag = '0' FOR UPDATE")
    SoSalesOrderHeader lockHeader(@Param("tenantId") String tenantId, @Param("soId") Long soId);

    /** 头锁之后按来源行 ID 顺序锁定全部关联行，并返回同一锁内的数量、预留和质检状态。 */
    @Select({"<script>",
        "SELECT line_id AS lineId, tenant_id AS tenantId, so_id AS soId, material_id AS materialId,",
        "ordered_qty AS orderedQty, delivered_qty AS deliveredQty, reserved_qty AS reservedQty,",
        "line_status AS lineStatus, oqc_status AS oqcStatus, del_flag AS delFlag FROM so_sales_order_line",
        "WHERE tenant_id = #{tenantId} AND so_id = #{soId} AND del_flag = '0' AND line_id IN",
        "<foreach collection='lineIds' item='lineId' open='(' separator=',' close=')'>#{lineId}</foreach>",
        "ORDER BY line_id FOR UPDATE", "</script>"})
    List<SoSalesOrderLine> lockLines(@Param("tenantId") String tenantId, @Param("soId") Long soId,
                                    @Param("lineIds") List<Long> lineIds);
}
