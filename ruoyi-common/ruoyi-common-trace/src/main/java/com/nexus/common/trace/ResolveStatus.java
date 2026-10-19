package com.nexus.common.trace;

/**
 * 多对象命中显式返回歧义，禁止用优先级代替业务确认。
 */
public enum ResolveStatus {
    /**
     * 已经唯一确定有权访问的业务对象。
     */
    RESOLVED,
    /**
     * 仍有多个有权候选，必须显式选择。
     */
    AMBIGUOUS,
    /**
     * 当前权限及业务上下文内没有匹配对象。
     */
    NOT_FOUND,
    /**
     * 原码或业务上下文不满足解析约束。
     */
    INVALID
}
