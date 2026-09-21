package com.nexus.mes.dhr.domain.vo;

import com.nexus.common.sign.domain.vo.VerifyResultVo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DHR 批记录校验结果（GET /mes/dhr/{dhrId}/verify）。
 *
 * @author Nexus
 */
@Data
public class DhrVerifyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 落库的快照总哈希 */
    private String snapshotHash;

    /** 用当前分册正文重算的快照总哈希 */
    private String recomputedHash;

    /** 快照哈希是否一致（且每个分册正文哈希均可复现） */
    private Boolean hashMatched;

    /** 签名链校验结果（DHR_SUBMIT / DHR_RELEASE / DHR_REJECT 三条链合并） */
    private VerifyResultVo chainVerify;

    /** 结论：PASS / FAIL */
    private String verdict;
}
