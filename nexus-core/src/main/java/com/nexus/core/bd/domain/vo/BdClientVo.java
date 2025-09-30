
package com.nexus.core.bd.domain.vo;

import com.nexus.common.core.plus.dto.BasePageDtoRequest;
import lombok.Data;

/**
 * 客户信息对象 bd_client vo
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
public class BdClientVo extends BasePageDtoRequest {

    /**
     * 客户ID
     */
    private Long clientId;
    /**
     * 客户编码
     */
    private String clientCode;
    /**
     * 客户名称
     */
    private String clientName;
    /**
     * 客户简写
     */
    private String clientNick;
    /**
     * 客户英文名称
     */
    private String clientEn;
    /**
     * 客户简介
     */
    private String clientDesc;
    /**
     * 客户类型
     */
    private String clientType;
    /**
     * 客户LOGO
     */
    private String clientLogo;
    /**
     * 客户电话
     */
    private String tel;
    /**
     * 统一社会信用代码
     */
    private String creditCode;

}