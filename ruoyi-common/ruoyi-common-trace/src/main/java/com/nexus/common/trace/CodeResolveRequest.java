package com.nexus.common.trace;

/**
 * 只读解析请求；租户、人员和设备权限来自认证上下文。
 *
 * @param rawCode 保留原文的扫描输入。
 * @param scene 由业务入口约束的扫描场景。
 * @param materialId 当前业务行实际物料标识。
 * @param supplierId 有权访问的供应商主键。
 * @param customerId 有权访问的客户主键。
 * @param sourceDocType 原业务单据类型。
 * @param sourceDocId 原业务单据主键。
 * @param lineId 原单业务行主键。
 */
public record CodeResolveRequest(
        String rawCode,
        String scene,
        Long materialId,
        Long supplierId,
        Long customerId,
        String sourceDocType,
        Long sourceDocId,
        Long lineId) {}
