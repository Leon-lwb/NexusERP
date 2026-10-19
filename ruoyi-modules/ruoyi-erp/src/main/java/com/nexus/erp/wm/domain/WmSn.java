package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 序列号管理对象 wm_sn
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_sn")
public class WmSn extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 序列号ID
     */
    @TableId(value = "sn_id")
    private Long snId;

    /**
     * 序列号编码
     */
    @ExcelProperty("序列号编码")
    private String snCode;

    /**
     * 物料ID
     */
    @ExcelProperty("物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty("规格型号")
    private String materialSpec;

    /**
     * 关联批次ID
     */
    private Long batchId;

    /**
     * 关联批次编码
     */
    @ExcelProperty("关联批次编码")
    private String batchCode;

    /**
     * 当前仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 当前库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty("库位编码")
    private String locationCode;

    /**
     * 关联库存记录ID
     */
    private Long stockId;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * ERP统一注册命令；正式号码一经预留永久占用。
     */
    private String registrationCommand;

    /**
     * 正式实物身份版本，由受控业务动作维护。
     */
    private Long identityVersion;

    /**
     * 来源单据类型
     */
    @ExcelProperty("来源单据类型")
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    private Long sourceDocId;

    /**
     * 来源单据编号
     */
    @ExcelProperty("来源单据编号")
    private String sourceDocNo;

    /**
     * 生成规则ID
     */
    private Long ruleId;

    /**
     * 生产日期
     */
    @ExcelProperty("生产日期")
    private Date productionDate;

    /**
     * 有效期
     */
    @ExcelProperty("有效期")
    private Date expiryDate;

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

    /**
     * 删除标志
     */
    @TableLogic private String delFlag;
}
