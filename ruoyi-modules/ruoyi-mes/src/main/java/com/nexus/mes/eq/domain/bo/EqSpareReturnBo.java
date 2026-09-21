package com.nexus.mes.eq.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES备件退库单业务对象（新增/编辑/查询，MES-EQ-04 收口）
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EqSpareReturnBo extends BaseEntity {

    /** 退库单ID(编辑必填) */
    private Long returnId;

    /** 退库单号(留空由服务端按 MESRT+yyyyMMdd+4位当日序号 生成) */
    private String returnNo;

    /** 退库目标 ERP 仓库ID */
    private Long warehouseId;

    /** 退库日期(留空取当前时间) */
    private Date returnDate;

    /** 退库类型 REPAIR_RETURN / EXCESS_RETURN / OTHER */
    private String returnType;

    /** 状态(仅查询条件使用, 新增/编辑由服务端控制) */
    private String status;

    /** 明细数量合计(由服务端按明细汇总) */
    private BigDecimal totalQty;

    /** 备注 */
    private String remark;

    /** 查询条件: 退库日期起(含), 形如 2026-09-01 */
    private String beginTime;

    /** 查询条件: 退库日期止(含), 只传日期时自动补 23:59:59 */
    private String endTime;

    /** 明细行 */
    private List<EqSpareReturnLineBo> lines;
}
