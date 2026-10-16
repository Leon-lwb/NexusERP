package com.nexus.ai.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.engineering.security.EngineeringAccess;
import com.nexus.ai.engineering.service.EngineeringArtifactService;
import com.nexus.common.ai.engineering.EngineeringArtifactManifest;
import com.nexus.common.core.domain.R;
import com.nexus.common.tenant.helper.TenantHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 私有产物读取接口，每次请求重新核对当前 owner 及权限，不使用永久 URL。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/engineering")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringArtifactController {
    private final EngineeringArtifactService artifacts;
    private final EngineeringAccess access;

    /** 读取本人任务的当前确认清单。 */
    @GetMapping("/tasks/{taskId}/manifest")
    @SaCheckPermission("ai:engineering:query")
    public R<EngineeringArtifactManifest> manifest(@PathVariable Long taskId) {
        var actor = access.current("ai:engineering:query", false);
        return TenantHelper.dynamic(
                actor.tenantId(), () -> R.ok(artifacts.manifest(actor, taskId)));
    }

    /** 代理私有对象流；仅 PNG/JPEG 图片允许内联，其余均为安全附件。 */
    @GetMapping("/artifacts/{artifactId}/content")
    @SaCheckPermission("ai:engineering:download")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long artifactId)
            throws IOException {
        var actor = access.current("ai:engineering:download", false);
        try {
            return TenantHelper.dynamic(
                    actor.tenantId(),
                    () -> {
                        try {
                            var content = artifacts.open(actor, artifactId);
                            String disposition =
                                    (content.inline()
                                                    ? ContentDisposition.inline()
                                                    : ContentDisposition.attachment())
                                            .filename(
                                                    content.originalFilename(),
                                                    StandardCharsets.UTF_8)
                                            .build()
                                            .toString();
                            return ResponseEntity.ok()
                                    .contentType(MediaType.parseMediaType(content.mimeType()))
                                    .contentLength(content.size())
                                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                                    .header("X-Content-Type-Options", "nosniff")
                                    .body(new InputStreamResource(content.input()));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** 返回截断有标记的规范 BOM/DRC，避免预览一次性解析超量原始行。 */
    @GetMapping("/artifacts/{artifactId}/report")
    @SaCheckPermission("ai:engineering:download")
    public R<Object> report(@PathVariable Long artifactId) {
        var actor = access.current("ai:engineering:download", false);
        return TenantHelper.dynamic(
                actor.tenantId(), () -> R.ok(artifacts.report(actor, artifactId)));
    }
}
