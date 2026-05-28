package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 车间设置对象 bd_workshop
 *
 * @author nexus
 * @date 2025-11-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_workshop")
public class BdWorkshop extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 车间ID
     */
    @TableId(value = "workshop_id")
    private Long workshopId;

    /**
     * 车间编码
     */
    @ExcelProperty("车间编码")
    private String workshopCode;

    /**
     * 车间名称
     */
    @ExcelProperty("车间名称")
    private String workshopName;

    /**
     * 车间描述
     */
    @ExcelProperty("车间描述")
    private String workshopDesc;

    /**
     * 车间主任ID
     */
    private Long managerId;

    /**
     * 车间主任
     */
    @ExcelProperty("车间主任")
    private String managerName;

    /**
     * 产能（标准工时/天）
     */
    @ExcelProperty("产能")
    private BigDecimal capacity;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Long status;

    /**
     * 排序
     */
    @ExcelProperty("排序")
    private Long sortOrder;

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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
