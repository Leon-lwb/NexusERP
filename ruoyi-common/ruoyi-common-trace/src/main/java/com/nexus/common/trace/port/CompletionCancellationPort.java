package com.nexus.common.trace.port;
import com.nexus.common.trace.*;

/** 已验证的取消准备凭据进入 ERP inbox 写事务；同事务提交结果与 outbox，返回后才可 ACK。 */
public interface CompletionCancellationPort {
    CompletionCancellationResult cancel(CompletionCancellationProof proof);
}
