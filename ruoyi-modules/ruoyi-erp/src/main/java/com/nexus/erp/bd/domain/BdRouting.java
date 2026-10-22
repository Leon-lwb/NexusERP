package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 工艺路线主表对象 bd_routing
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_routing")
public class BdRouting extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工艺路线ID
     */
    @TableId(value = "routing_id")
    private Long routingId;

    /**
     * 工艺路线编码
     */
    @ExcelProperty("工艺路线编码")
    private String routingCode;

    /**
     * 工艺路线名称
     */
    @ExcelProperty("工艺路线名称")
    private String routingName;

    /**
     * 工艺路线描述
     */
    @ExcelProperty("工艺路线描述")
    private String routingDesc;

    /**
     * 适用物料ID
     */
    @ExcelProperty("适用物料ID")
    private Long materialId;

    /**
     * 适用物料编码
     */
    @ExcelProperty("适用物料编码")
    private String materialCode;

    /**
     * 适用物料名称
     */
    @ExcelProperty("适用物料名称")
    private String materialName;

    /**
     * 版本号
     */
    @ExcelProperty("版本号")
    private String routingVersion;

    /**
     * 是否当前版本
     */
    @ExcelProperty("是否当前版本")
    private Long isCurrent;

    /**
     * 总工序数
     */
    @ExcelProperty("总工序数")
    private Integer totalSteps;

    /**
     * 总标准工时(小时)
     */
    @ExcelProperty("总标准工时")
    private BigDecimal totalHours;

    /**
     * 生效日期
     */
    @ExcelProperty("生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty("失效日期")
    private Date expiryDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
