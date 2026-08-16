package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmBarcode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 条码实例视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmBarcode.class)
public class WmBarcodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 条码实例ID
     */
    private Long barcodeId;

    /**
     * 条码
     */
    @ExcelProperty(value = "条码")
    private String barcodeCode;

    /**
     * 配置ID
     */
    @ExcelProperty(value = "配置ID")
    private Long configId;

    /**
     * 配置编码
     */
    @ExcelProperty(value = "配置编码")
    private String configCode;

    /**
     * 业务类型
     */
    @ExcelProperty(value = "业务类型")
    private String bizType;

    /**
     * 关联业务ID
     */
    @ExcelProperty(value = "关联业务ID")
    private Long bizId;

    /**
     * 关联业务行ID
     */
    @ExcelProperty(value = "关联业务行ID")
    private Long bizLineId;

    /**
     * 物料ID
     */
    @ExcelProperty(value = "物料ID")
    private Long materialId;

    /**
     * 批次ID
     */
    @ExcelProperty(value = "批次ID")
    private Long batchId;

    /**
     * 库存ID
     */
    @ExcelProperty(value = "库存ID")
    private Long stockId;

    /**
     * 序列号ID
     */
    private Long snId;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 生成时间
     */
    @ExcelProperty(value = "生成时间")
    private Date generateTime;

    /**
     * 绑定时间
     */
    @ExcelProperty(value = "绑定时间")
    private Date bindTime;

    /**
     * 失效时间
     */
    @ExcelProperty(value = "失效时间")
    private Date expireTime;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}
