package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSn;

import java.util.Date;

/**
 * 序列号管理业务对象 wm_sn
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSn.class, reverseConvertGenerate = false)
public class WmSnBo extends BaseEntity {

    /** 调用方持久化的注册命令号；重试必须使用原值，不允许服务端代造。 */
    private String commandId;

    /**
     * 序列号ID
     */
    private Long snId;

    /**
     * 序列号编码
     */
    private String snCode;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 关联批次ID
     */
    private Long batchId;

    /**
     * 关联批次编码
     */
    private String batchCode;

    /**
     * 当前仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 当前库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 关联库存记录ID
     */
    private Long stockId;

    /**
     * 状态
     */
    private String status;

    /**
     * 来源单据类型
     */
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    private Long sourceDocId;

    /**
     * 来源单据编号
     */
    private String sourceDocNo;

    /**
     * 生成规则ID
     */
    private Long ruleId;

    /**
     * 生产日期
     */
    private Date productionDate;

    /**
     * 有效期
     */
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
     * 批量生成数量（仅用于batchGenerate接口）
     */
    private Integer quantity;
}
