package com.nexus.common.sign.service;

import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;

import java.util.List;

/**
 * 电子签名统一入口。
 *
 * <p>P1 仅实现「逻辑签名」（不产出新文件）；PDF 盖章在 P2 通过
 * {@code signDocument} 扩展，信任根通过 SPI 预留。</p>
 *
 * @author nexus
 */
public interface ESignatureService {

    /**
     * 逻辑签名：审批签批 / SOP 查阅 / NCR 处置。
     *
     * @param cmd 签名入参
     * @return 签名ID（回填业务表的 signature_id 字段）
     */
    Long sign(SignCommand cmd);

    /**
     * 验签：校验单条记录自身哈希 + 与上一条的链接。
     */
    VerifyResultVo verify(Long signatureId);

    /**
     * 全链校验：对该业务单据的全部签名按序重算并校验链接。
     */
    VerifyResultVo verifyChain(String bizType, Long bizId);

    /**
     * 按业务查询签名列表（按签署时间正序）。
     */
    List<SignatureVo> listByBiz(String bizType, Long bizId);
}
