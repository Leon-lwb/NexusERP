package com.nexus.common.sign.domain.bo;

import com.nexus.common.sign.domain.EsSealGrant;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 用章授权新增/修改入参。
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSealGrant.class, reverseConvertGenerate = false)
public class SealGrantBo {

    private Long grantId;

    @NotNull(message = "印章不能为空")
    private Long sealId;

    /** 被授权用户ID（与 granteeDeptId 至少填一个） */
    private Long granteeUserId;

    /** 被授权部门ID（与 granteeUserId 至少填一个） */
    private Long granteeDeptId;

    /** 限定业务场景(NULL=不限) */
    private String bizType;

    /** 可否单独用章(0否 1是) */
    private String canSignAlone;

    private Date validFrom;

    private Date validTo;

    private String status;

    private String remark;
}
