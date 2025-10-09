
package com.nexus.core.bd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 物料产品主对象 bd_material vo
 *
 * @author nexus
 * @date 2025-10-04
 */
@Data
public class BdMaterialVo extends BasePageDtoRequest {

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
     * 物料类型
     */
    private Long materialType;
    /**
     * 来源类型
     */
    private Long sourceType;
    /**
     * 是否批次管理
     */
    private Long isBatchManage;
    /**
     * 是否序列号管理
     */
    private Long isSerialManage;
    /**
     * 状态
     */
    private Long status;
    /**
     * 质量状态
     */
    private Long qualityStatus;

}