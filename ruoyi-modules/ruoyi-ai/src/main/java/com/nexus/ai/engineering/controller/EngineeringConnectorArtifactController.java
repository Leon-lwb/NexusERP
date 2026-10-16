package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaIgnore;

import com.nexus.ai.engineering.domain.EngineeringUploadRequest;
import com.nexus.ai.engineering.security.EngineeringConnectorFilter;
import com.nexus.ai.engineering.service.EngineeringArtifactUploadService;
import com.nexus.common.core.domain.R;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 当前设备专属的任务绑定上传接口，必须同时通过设备认证和短期票据验证。 */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering/connector/tasks/{taskId}")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringConnectorArtifactController {
    private final EngineeringArtifactUploadService uploads;
    private final com.nexus.ai.engineering.service.EngineeringArtifactService artifacts;

    /** 当前设备可读取部分清单，确认上传丢响应时不会重复归档。 */
    @GetMapping("/manifest")
    public R<com.nexus.common.ai.engineering.EngineeringArtifactManifest> manifest(
            HttpServletRequest http, @PathVariable Long taskId) {
        return R.ok(artifacts.deviceManifest(EngineeringConnectorFilter.principal(http), taskId));
    }

    /** 由服务器已确认文件与真实DRC报告计算终态，不接受客户端state字段。 */
    @PostMapping("/complete")
    public R<com.nexus.ai.engineering.domain.EngineeringTaskVo> complete(
            HttpServletRequest http,
            @PathVariable Long taskId,
            @RequestBody com.nexus.ai.engineering.domain.EngineeringLease lease) {
        return R.ok(artifacts.complete(EngineeringConnectorFilter.principal(http), taskId, lease));
    }

    /** 申请绑定类型、路径、大小和真实摘要的五分钟票据。 */
    @PostMapping("/upload-ticket")
    public R<EngineeringArtifactUploadService.Ticket> ticket(
            HttpServletRequest http,
            @PathVariable Long taskId,
            @RequestBody EngineeringUploadRequest request) {
        return R.ok(uploads.ticket(EngineeringConnectorFilter.principal(http), taskId, request));
    }

    /** multipart 字段固定为 file，元信息只能来自服务端已消费票据。 */
    @PostMapping("/artifacts")
    public R<EngineeringArtifactUploadService.Uploaded> upload(
            HttpServletRequest http,
            @PathVariable Long taskId,
            @RequestHeader("X-Nexus-Upload-Ticket") String ticket,
            @RequestPart("file") MultipartFile file) {
        return R.ok(
                uploads.upload(EngineeringConnectorFilter.principal(http), taskId, ticket, file));
    }
}
