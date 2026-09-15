package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用章授权视图对象。
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSealGrant.class)
public class SealGrantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权ID */
    private Long grantId;

    /** 印章ID */
    private Long sealId;

    /** 印章名称（服务层联表 EsSeal 填充） */
    private String sealName;

    /** 被授权用户ID */
    private Long granteeUserId;

    /** 被授权用户昵称（序列化时按 granteeUserId 翻译） */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "granteeUserId")
    private String granteeUserName;

    /** 被授权部门ID */
    private Long granteeDeptId;

    /** 被授权部门名称（序列化时按 granteeDeptId 翻译） */
    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "granteeDeptId")
    private String granteeDeptName;

    /** 限定业务场景(NULL=不限) */
    private String bizType;

    /** 可否单独用章(0否 1是) */
    private String canSignAlone;

    /** 授权生效时间 */
    private Date validFrom;

    /** 授权失效时间 */
    private Date validTo;

    /** 状态(0停用 1启用) */
    private String status;

    /** 授权人用户ID */
    private Long grantBy;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}
