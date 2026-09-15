package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 验签结果。
 *
 * @author nexus
 */
@Data
public class VerifyResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 整体是否通过 */
    private Boolean passed;

    /** 链校验是否通过 */
    private Boolean chainPassed;

    /** 断链位置（首个不一致的 signatureId；无断链为 null） */
    private Long brokenAt;

    /** 校验的记录条数 */
    private Integer checkedCount;

    /** 明细说明 */
    private List<String> messages = new ArrayList<>();
}
