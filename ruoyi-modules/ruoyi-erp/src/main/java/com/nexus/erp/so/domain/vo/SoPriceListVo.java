package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoPriceList;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售价格表视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoPriceList.class)
public class SoPriceListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 价格表ID */
    private Long priceListId;

    /** 价格表编码 */
    @ExcelProperty(value = "价格表编码")
    private String listCode;

    /** 价格表名称 */
    @ExcelProperty(value = "价格表名称")
    private String listName;

    /** 价格表类型 */
    @ExcelProperty(value = "价格表类型")
    private String listType;

    /** 客户ID */
    private Long clientId;

    /** 客户名称（展示用，selectPageList/selectById 经 clientId 回填，不落库） */
    private String clientName;

    /** 币种 */
    @ExcelProperty(value = "币种")
    private String currency;

    /** 优先级 */
    @ExcelProperty(value = "优先级")
    private Integer priority;

    /** 生效日期 */
    @ExcelProperty(value = "生效日期")
    private LocalDate validFrom;

    /** 失效日期 */
    @ExcelProperty(value = "失效日期")
    private LocalDate validTo;

    /** 状态 */
    @ExcelProperty(value = "状态")
    private String status;

    /** 价格表行列表 */
    private List<SoPriceListLineVo> soPriceListLineList;
}
