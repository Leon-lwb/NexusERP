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
     * 独立事务签名（REQUIRES_NEW）。
     * <p>供"签署失败可降级、不得拖垮调用方事务"的场景使用（如 MES SOP 查阅确认）。
     * 失败只回滚自身事务，不会把调用方事务标记为 rollback-only。</p>
     *
     * <p><b>为什么必须存在这个入口（勿删勿简化）：</b>{@link #sign} 的默认传播行为是
     * {@code REQUIRED} —— 它会<b>加入</b>调用方事务。若调用方仅用 try/catch 包住
     * {@code sign(...)} 以求"失败即降级"，签名方法抛出的
     * {@code RuntimeException} 会在抛出前先把<b>共享</b>事务标记为 rollback-only，
     * 调用方的 catch 只能吞掉异常、吞不掉这个标记：调用方后续的写入看起来成功，
     * 提交时却抛 {@code UnexpectedRollbackException}，把本该被保护的业务写
     * （如查阅记录落库）一并回滚 —— 即"降级"落空。改用本方法后签名自开事务，
     * 失败只影响它自己，调用方的 {@code try/catch} 才真正具备降级语义。</p>
     *
     * <p>注意：事务边界只在 Spring 代理调用处生效，同类内部方法调用不会开启新事务。</p>
     *
     * @param cmd 签名入参
     * @return 签名ID
     */
    Long signInNewTransaction(SignCommand cmd);

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
