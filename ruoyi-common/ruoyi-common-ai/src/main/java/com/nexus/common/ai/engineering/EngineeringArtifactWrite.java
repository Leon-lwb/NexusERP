package com.nexus.common.ai.engineering;

import java.nio.file.Path;

/**
 * 已经服务器验真的暂存产物，路径由服务器生成，不来自设备请求。
 *
 * @param actor 服务端已认证操作者，不能从请求正文提供
 * @param taskId 服务端任务编号，对外使用十进制字符串
 * @param artifactId 服务端产物编号，对外使用十进制字符串
 * @param artifactType 协议限定的产物类别
 * @param stagedPath 服务器生成且已经验真的普通暂存文件
 * @param sha256 服务器验真的完整 SHA-256
 * @param size 服务器验真的真实字节数
 * @param mimeType 服务器格式检测所得媒体类型
 * @param originalFilename 仅用于安全下载显示的文件名，不是存储键
 */
public record EngineeringArtifactWrite(
        EngineeringActor actor,
        Long taskId,
        Long artifactId,
        EngineeringArtifactType artifactType,
        Path stagedPath,
        String sha256,
        long size,
        String mimeType,
        String originalFilename) {}
