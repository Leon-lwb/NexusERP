package com.nexus.common.sign.domain.bo;

import lombok.Data;

/**
 * 印章查询条件。
 *
 * @author nexus
 */
@Data
public class SealQueryBo {

    private String sealType;

    private String sealName;

    private String status;

    /** 仅查当前用户可用的印章（个人章 + 已授权公司章） */
    private Boolean onlyAvailable;
}
