package org.dromara.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_bom_detail")
public class BdBomDetail extends TenantEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "detail_id")
    private Long detailId;

    @ExcelProperty("BOM主表ID")
    private Long bomId;

    @ExcelProperty("子项物料ID")
    private Long childMaterialId;

    @ExcelProperty("单位父项用量")
    private BigDecimal quantityPerParent;

    @ExcelProperty("损耗率(%)")
    private BigDecimal lossRate;

    @ExcelProperty("工序步骤")
    private Integer operationStep;

    @ExcelProperty("工作中心ID")
    private Long workstationId;

    @ExcelProperty("提前期(小时)")
    private Integer leadTime;

    @ExcelProperty("生效日期")
    private LocalDate effectiveDate;

    @ExcelProperty("失效日期")
    private LocalDate expiryDate;

    @ExcelProperty("显示排序")
    private Integer sortOrder;

    @ExcelProperty("状态")
    private Integer status;

    private String remark;

    @ExcelProperty("来源类型")
    private String sourceType;

    private String attr1;
    private String attr2;
    private Integer attr3;
    private Integer attr4;

    @TableLogic
    private String delFlag;
}
