package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/**
 * 可恢复的发号结果；已预留号码永久占用，业务取消不会回收。
 *
 * @param reservationId 正式身份预留单标识。
 * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
 * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
 * @param identities 统一登记服务返回的正式身份集合。
 * @param status 解析或业务处理的明确状态。
 */
public record SerialReservation(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long reservationId,
        String commandId,
        String fingerprint,
        List<SerialIdentity> identities,
        String status) {
    public SerialReservation {
        identities = List.copyOf(identities);
    }
}
