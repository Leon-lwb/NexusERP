package com.nexus.common.ai.engineering;

/**
 * 经过当前用户状态复核的设备身份，不携带任何凭证原文。
 *
 * @param deviceId 当前用户显式绑定的设备编号
 * @param actor 服务端已认证操作者，不能从请求正文提供
 * @param generation 当前凭证代数
 */
public record EngineeringDevicePrincipal(Long deviceId, EngineeringActor actor, long generation) {}
