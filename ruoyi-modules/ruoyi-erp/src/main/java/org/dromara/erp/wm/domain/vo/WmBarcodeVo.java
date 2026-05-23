package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmBarcode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 条码实例视图对象
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmBarcode.class)
public class WmBarcodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long barcodeId;

    @ExcelProperty(value = "条码")
    private String barcodeCode;

    @ExcelProperty(value = "配置ID")
    private Long configId;

    @ExcelProperty(value = "配置编码")
    private String configCode;

    @ExcelProperty(value = "业务类型")
    private String bizType;

    @ExcelProperty(value = "关联业务ID")
    private Long bizId;

    @ExcelProperty(value = "关联业务行ID")
    private Long bizLineId;

    @ExcelProperty(value = "物料ID")
    private Long materialId;

    @ExcelProperty(value = "批次ID")
    private Long batchId;

    @ExcelProperty(value = "库存ID")
    private Long stockId;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "生成时间")
    private Date generateTime;

    @ExcelProperty(value = "绑定时间")
    private Date bindTime;

    @ExcelProperty(value = "失效时间")
    private Date expireTime;

    @ExcelProperty(value = "备注")
    private String remark;
}
