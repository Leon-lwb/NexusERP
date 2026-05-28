package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.wm.domain.WmStockReconciliation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存台账校验视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmStockReconciliation.class)
public class WmStockReconciliationVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "校验ID")
    private Long reconId;

    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    @ExcelProperty(value = "账面库存")
    private BigDecimal bookQty;

    @ExcelProperty(value = "事务汇总")
    private BigDecimal txBalance;

    @ExcelProperty(value = "差异数量")
    private BigDecimal diffQty;

    @ExcelProperty(value = "校验状态")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "校验时间")
    private Date reconTime;

    @ExcelProperty(value = "备注")
    private String remark;
}
