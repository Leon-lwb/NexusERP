package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

/**
 * 已验证的取消准备凭据进入 ERP inbox 写事务；同事务提交结果与 outbox，返回后才可 ACK。
 */
public interface CompletionCancellationPort {
    /**
     * 凭已实证的原事件与取消事件，在所属库存事务中建立取消屏障并返回不可变结果。
     *
     * @param proof 在事务外核验真实来源后签发的进程内凭据。
     * @return 包含取消事件、原清单及取消结果的不可变凭据。
     */
    CompletionCancellationResult cancel(CompletionCancellationProof proof);
}
