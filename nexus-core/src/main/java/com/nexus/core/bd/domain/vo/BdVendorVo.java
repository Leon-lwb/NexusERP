
package com.nexus.core.bd.domain.vo;

import com.nexus.common.core.plus.dto.BasePageDtoRequest;
import lombok.Data;

/**
 * 供应商对象 bd_vendor vo
 *
 * @author nexus
 * @date 2025-09-22
 */
@Data
public class BdVendorVo extends BasePageDtoRequest {

    /**
     * 供应商ID
     */
    private Long vendorId;
    /**
     * 供应商编码
     */
    private String vendorCode;
    /**
     * 供应商名称
     */
    private String vendorName;
    /**
     * 行业
     */
    private String industry;
    /**
     * 地区
     */
    private String region;
    /**
     * 合作状态 
     */
    private String status;

}