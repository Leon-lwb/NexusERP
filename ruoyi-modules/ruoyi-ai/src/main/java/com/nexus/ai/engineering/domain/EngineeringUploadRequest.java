package com.nexus.ai.engineering.domain;

import com.nexus.common.ai.engineering.EngineeringArtifactType;

/**
 * 单文件上传票据请求，元信息与 task/attempt/device 和摘要绑定。
 *
 * @param attempt 本次领取尝试编号，旧尝试禁止写入
 * @param leaseToken 随机租约原文，禁止存储到日志或普通任务视图
 * @param expectedRevision 调用者期望的当前修订号，用于 CAS
 * @param artifactType 协议限定的产物类别
 * @param groupId OBJ、MTL 与纹理的同组标识
 * @param relativePath 组内规范相对路径，禁止盘符、URL、父目录和控制字符
 * @param sha256 服务器验真的完整 SHA-256
 * @param size 服务器验真的真实字节数
 * @param mimeType 服务器格式检测所得媒体类型
 * @param originalFilename 仅用于安全下载显示的文件名，不是存储键
 */
public record EngineeringUploadRequest(
        int attempt,
        String leaseToken,
        long expectedRevision,
        EngineeringArtifactType artifactType,
        String groupId,
        String relativePath,
        String sha256,
        long size,
        String mimeType,
        String originalFilename) {
    /** 提取当前领取租约。 */
    public EngineeringLease lease() {
        return new EngineeringLease(attempt, leaseToken, expectedRevision);
    }

    @Override
    public String toString() {
        return "EngineeringUploadRequest[产物元信息，租约已隐藏]";
    }
}
