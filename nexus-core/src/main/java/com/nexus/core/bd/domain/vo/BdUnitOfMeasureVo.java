
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 计量单位对象 bd_unit_of_measure vo
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
public class BdUnitOfMeasureVo extends BasePageDtoRequest {

    /**
     * 单位ID
     */
    private Long uomId;
    /**
     * 单位编码
     */
    private String uomCode;
    /**
     * 单位名称
     */
    private String uomName;
    /**
     * 单位符号(kg、m、pcs等)
     */
    private String uomSymbol;
    /**
     * 单位类别(重量、长度、数量等)
     */
    private String category;
    /**
     * 是否基本单位 0:否 1:是
     */
    private Long isBase;
    /**
     * 状态 0:停用 1:启用
     */
    private Long status;
    /**
     * 排序
     */
    private Long sortOrder;

}