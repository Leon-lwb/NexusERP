package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoContractLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售合同明细行视图对象 so_contract_line
 *
 * <p>字段集 = 实体减去审计字段（tenantId/delFlag/createBy/createDept/createTime/updateBy/updateTime），
 * 保留全部业务字段，避免合同表单加载时丢字段、保存时回写 null。</p>
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoContractLine.class)
public class SoContractLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行ID */
    private Long lineId;

    /** 合同ID */
    private Long contractId;

    /** 行号 */
    @ExcelProperty("行号")
    private String lineNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 物料规格 */
    @ExcelProperty("物料规格")
    private String materialSpec;

    /** 数量 */
    @ExcelProperty("数量")
    private BigDecimal quantity;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 单价 */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /** 行金额 */
    @ExcelProperty("行金额")
    private BigDecimal lineAmount;

    /** 税率（百分数：13 表示 13%） */
    @ExcelProperty("税率")
    private BigDecimal taxRate;

    /** 税额 */
    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    /** 交货日期 */
    @ExcelProperty("交货日期")
    private Date deliveryDate;

    /** 已发货数量（预留：履约跟踪，当前业务未读写） */
    private BigDecimal deliveredQty;

    /** 已开票数量（预留：履约跟踪，当前业务未读写） */
    private BigDecimal invoicedQty;

    /** 行状态（预留：履约跟踪，当前业务未读写） */
    private String lineStatus;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}
