package com.nexus.common.ai.engineering;

/**
 * 私有归档内部定位信息，禁止将对象键、服务商和 OSS 编号输出给模型或浏览器。
 *
 * @param ossId 仅供内部定位的 OSS 元数据编号，不对外输出
 * @param service 显式私有存储配置键，不对外输出
 * @param objectKey 服务端生成的工程专属对象键，不对外输出
 * @param size 服务器验真的真实字节数
 * @param sha256 服务器验真的完整 SHA-256
 */
public record EngineeringArtifactStored(
        Long ossId, String service, String objectKey, long size, String sha256) {}
