package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoPriceList;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售价格表业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoPriceList.class, reverseConvertGenerate = false)
public class SoPriceListBo extends BaseEntity {

    /** 价格表ID */
    private Long priceListId;

    /** 价格表编码（为空时由后端按 PRICE_LIST_CODE 规则自动生成，故不强制必填） */
    private String listCode;
    /** 价格表名称 */
    @NotBlank(message = "价格表名称不能为空")
    private String listName;
    /** 价格表类型 */
    @NotBlank(message = "价格表类型不能为空")
    private String listType;
    /** 客户ID */
    private Long clientId;
    /** 币种 */
    private String currency;
    /** 优先级 */
    private Integer priority;
    /** 生效日期 */
    private LocalDate validFrom;
    /** 失效日期 */
    private LocalDate validTo;
    /** 状态 */
    private String status;

    /** 价格表行列表 */
    private List<SoPriceListLineBo> soPriceListLineList;

    /** ID数组（用于批量操作） */
    private Long[] ids;
}
