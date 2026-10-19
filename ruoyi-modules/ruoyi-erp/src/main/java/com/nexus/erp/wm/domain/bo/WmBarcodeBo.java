package com.nexus.erp.wm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.erp.wm.domain.WmBarcode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 条码实例业务对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmBarcode.class, reverseConvertGenerate = false)
public class WmBarcodeBo extends BaseEntity {

    /**
     * 条码实例ID
     */
    private Long barcodeId;

    /**
     * 条码内容
     */
    @NotBlank(message = "条码不能为空")
    private String barcodeCode;

    /**
     * 登记时冻结的主对象类型；历史空值保留证据缺口。
     */
    private String primaryObjectType;

    /**
     * 条码配置ID
     */
    private Long configId;

    /**
     * 条码配置编码
     */
    private String configCode;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务单据ID
     */
    private Long bizId;

    /**
     * 业务单据行ID
     */
    private Long bizLineId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 序列号ID
     */
    private Long snId;

    /**
     * 库存ID
     */
    private Long stockId;

    /**
     * 状态
     */
    private String status;

    /**
     * 生成时间
     */
    private Date generateTime;

    /**
     * 绑定时间
     */
    private Date bindTime;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 备注
     */
    private String remark;
}
