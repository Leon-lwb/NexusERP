package com.nexus.mes.lt.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.lt.domain.LtSnBinding;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES SN批次绑定视图对象 lt_sn_binding
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = LtSnBinding.class)
public class LtSnBindingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 绑定ID */
    @ExcelProperty("绑定ID")
    private Long bindingId;

    /** SN ID */
    private Long snId;

    /** SN编码 */
    @ExcelProperty("SN编码")
    private String snCode;

    /** 批次ID */
    private Long lotId;

    /** 批次编码 */
    @ExcelProperty("批次编码")
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 绑定时间 */
    @ExcelProperty("绑定时间")
    private Date boundTime;

    /** 绑定人ID */
    private Long boundBy;
}
