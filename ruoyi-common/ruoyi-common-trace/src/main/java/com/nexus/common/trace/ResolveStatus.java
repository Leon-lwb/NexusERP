package com.nexus.common.trace;
/** 多对象命中显式返回歧义，禁止用优先级代替业务确认。 */
public enum ResolveStatus { RESOLVED, AMBIGUOUS, NOT_FOUND, INVALID }
