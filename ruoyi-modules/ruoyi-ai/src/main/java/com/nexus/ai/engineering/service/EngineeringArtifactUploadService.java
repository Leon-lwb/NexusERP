package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.security.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.engineering.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** 任务绑定票据和私有上传：验真、短事务预留、外部 IO、CAS 确认四个阶段分离。 */
@Service
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringArtifactUploadService {
    private final EngineeringRepository repository;
    private final EngineeringTaskService tasks;
    private final EngineeringDeviceService devices;
    private final EngineeringEphemeralStore ephemeral;
    private final EngineeringStoragePort storage;
    private final EngineeringProperties properties;
    private final Clock clock;

    /**
     * 申请五分钟单次上传票据；Redis 负载仅含绑定信息和租约摘要，绝不保存设备凭证或租约原文。
     *
     * @param principal 设备过滤器取得的当前设备身份
     * @param taskId 本设备当前任务编号
     * @param request 固定类型、组内路径、完整 SHA-256、大小和本次尝试
     * @return 短期 uploadTicket；相同已确认内容返回原 artifactId 且不重发票据
     * @throws EngineeringException 越权或过期租约、路径无效、超限文件、同路径内容冲突
     */
    public Ticket ticket(
            EngineeringDevicePrincipal principal, Long taskId, EngineeringUploadRequest request) {
        require(request != null, 422, "INVALID_REQUEST", "缺少上传元信息");
        var task = tasks.activeLease(principal, taskId, request.lease(), true, false);
        validateMetadata(request);
        var existing = findPath(task, request.relativePath());
        if (existing != null) {
            sameContent(existing, request);
            if ("CONFIRMED".equals(existing.getStatus()))
                return new Ticket(null, null, existing.getArtifactId().toString(), true);
            throw new EngineeringException(409, "UPLOAD_IN_PROGRESS", "该产物正在上传或等待清理，请读取清单后重试");
        }
        String token = EngineeringSecrets.token();
        String expires = clock.instant().plusSeconds(300).toString();
        var data =
                new TicketData(
                        principal.deviceId(),
                        principal.actor().tenantId(),
                        principal.actor().userId(),
                        taskId,
                        request.attempt(),
                        EngineeringSecrets.hash(request.leaseToken()),
                        expires,
                        request.artifactType(),
                        request.groupId(),
                        request.relativePath(),
                        request.sha256().toLowerCase(Locale.ROOT),
                        request.size(),
                        request.mimeType(),
                        request.originalFilename());
        ephemeral.put("upload", token, EngineeringJson.write(data), Duration.ofMinutes(5));
        return new Ticket(token, expires, null, false);
    }

    private void validateMetadata(EngineeringUploadRequest request) {
        require(request.artifactType() != null, 422, "INVALID_REQUEST", "未声明产物类型");
        EngineeringPaths.validate(request.relativePath());
        require(
                request.sha256() != null && request.sha256().matches("[A-Fa-f0-9]{64}"),
                422,
                "INVALID_REQUEST",
                "必须声明完整SHA-256摘要");
        require(
                request.size() > 0 && request.size() <= properties.getMaxArtifactBytes(),
                413,
                "UPLOAD_TOO_LARGE",
                "单文件超过允许大小");
        require(
                request.mimeType() == null
                        || request.mimeType().matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+"),
                422,
                "INVALID_REQUEST",
                "媒体类型格式无效");
        EngineeringPaths.filename(request.originalFilename());
        boolean model =
                Set.of(
                                EngineeringArtifactType.PCB_MODEL_OBJ,
                                EngineeringArtifactType.PCB_MODEL_MTL,
                                EngineeringArtifactType.PCB_MODEL_TEXTURE)
                        .contains(request.artifactType());
        require(
                (!model && request.groupId() == null)
                        || (request.groupId() != null
                                && request.groupId().matches("[A-Za-z0-9_-]{1,64}")),
                422,
                "INVALID_REQUEST",
                "模型产物必须声明安全的组标识");
    }

    /**
     * 验证设备和票据后流式暂存验真，短事务预留配额，事务外写私桶，再重新授权并 CAS 确认。
     *
     * @param principal 当前访问凭证认证的设备，确认前会再次复核代数、权限和到期时间
     * @param taskId 票据绑定任务编号
     * @param rawTicket X-Nexus-Upload-Ticket 的五分钟单次随机票据，禁止写日志
     * @param file multipart 字段 file，大小及摘要以服务端读取的真实流为准
     * @return 唯一已确认私有产物回执，不包含 OSS 对象键和 URL
     * @throws EngineeringException 票据不匹配或过期、摘要/格式错误、配额不足、取消竞争或私有归档失败
     */
    public Uploaded upload(
            EngineeringDevicePrincipal principal,
            Long taskId,
            String rawTicket,
            MultipartFile file) {
        devices.requireDevice(principal);
        require(
                rawTicket != null && rawTicket.length() <= 128,
                401,
                "UPLOAD_TICKET_INVALID",
                "缺少有效上传票据");
        String body = ephemeral.consume("upload", rawTicket);
        require(body != null, 401, "UPLOAD_TICKET_INVALID", "上传票据已使用或过期");
        var ticket = EngineeringJson.read(body, TicketData.class);
        require(
                Objects.equals(ticket.deviceId(), principal.deviceId())
                        && Objects.equals(ticket.tenantId(), principal.actor().tenantId())
                        && Objects.equals(ticket.userId(), principal.actor().userId())
                        && Objects.equals(ticket.taskId(), taskId),
                403,
                "UPLOAD_TICKET_INVALID",
                "上传票据与当前设备或任务不匹配");
        require(
                Instant.parse(ticket.expiresAt()).isAfter(clock.instant()),
                401,
                "UPLOAD_TICKET_INVALID",
                "上传票据已过期");
        tasks.uploadLease(principal, taskId, ticket.attempt(), ticket.leaseHash(), false);
        require(file != null, 422, "INVALID_REQUEST", "缺少multipart文件字段file");
        Path staged = null;
        AiEngineeringArtifact reserved = null;
        try {
            staged = Files.createTempFile("nexus-engineering-", ".upload");
            verifyStream(file, staged, ticket);
            var checked = EngineeringArtifactValidator.validate(staged, ticket.request());
            var reservation = reserve(principal, ticket, checked);
            if (reservation.alreadyConfirmed()) return uploaded(reservation.artifact());
            reserved = reservation.artifact();
            var stored =
                    storage.write(
                            new EngineeringArtifactWrite(
                                    principal.actor(),
                                    taskId,
                                    reserved.getArtifactId(),
                                    ticket.artifactType(),
                                    staged,
                                    ticket.sha256(),
                                    ticket.size(),
                                    checked.mimeType(),
                                    ticket.originalFilename()));
            require(
                    stored != null
                            && Objects.equals(stored.ossId(), reserved.getArtifactId())
                            && Objects.equals(stored.service(), properties.getOssConfigKey())
                            && Objects.equals(stored.objectKey(), reserved.getObjectKey())
                            && stored.size() == ticket.size()
                            && Objects.equals(stored.sha256(), ticket.sha256()),
                    503,
                    "STORAGE_UNAVAILABLE",
                    "私有归档返回信息不一致");
            final var row = reserved;
            return repository.transaction(
                    () -> {
                        tasks.uploadLease(
                                principal, taskId, ticket.attempt(), ticket.leaseHash(), true);
                        int changed =
                                repository.update(
                                        "update ai_engineering_artifact set"
                                            + " status='CONFIRMED',validation_json=?,mime_type=?"
                                            + " where artifact_id=? and tenant_id=? and user_id=?"
                                            + " and task_id=? and attempt=? and status='RESERVED'"
                                            + " and sha256=?",
                                        checked.validationJson(),
                                        checked.mimeType(),
                                        row.getArtifactId(),
                                        ticket.tenantId(),
                                        ticket.userId(),
                                        taskId,
                                        ticket.attempt(),
                                        ticket.sha256());
                        require(changed == 1, 409, "STALE_ATTEMPT", "任务已结束或产物确认状态已变化");
                        row.setStatus("CONFIRMED");
                        return uploaded(row);
                    });
        } catch (EngineeringException e) {
            if (reserved != null) abandon(reserved);
            throw e;
        } catch (Exception e) {
            if (reserved != null) abandon(reserved);
            throw new EngineeringException(503, "STORAGE_UNAVAILABLE", "工程产物暂未归档，请读取清单并稍后重试");
        } finally {
            if (staged != null)
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                    /* 临时文件仅由服务器生成，不输出本地路径。 */
                }
        }
    }

    private void verifyStream(MultipartFile file, Path staged, TicketData ticket)
            throws IOException, NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        byte[] buffer = new byte[32768];
        try (var input = file.getInputStream();
                var output =
                        Files.newOutputStream(
                                staged,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                require(
                        size <= properties.getMaxArtifactBytes(),
                        413,
                        "UPLOAD_TOO_LARGE",
                        "文件真实大小超过限制");
                require(size <= ticket.size(), 422, "UPLOAD_HASH_MISMATCH", "文件真实大小与票据声明不一致");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        require(
                size == ticket.size()
                        && HexFormat.of().formatHex(digest.digest()).equals(ticket.sha256()),
                422,
                "UPLOAD_HASH_MISMATCH",
                "文件大小或SHA-256与票据不一致");
    }

    private Reservation reserve(
            EngineeringDevicePrincipal principal,
            TicketData ticket,
            EngineeringArtifactValidator.Validated checked) {
        return repository.transaction(
                () -> {
                    var task =
                            tasks.uploadLease(
                                    principal,
                                    ticket.taskId(),
                                    ticket.attempt(),
                                    ticket.leaseHash(),
                                    true);
                    var existing = findPath(task, ticket.relativePath());
                    if (existing != null) {
                        sameContent(existing, ticket.request());
                        require(
                                "CONFIRMED".equals(existing.getStatus()),
                                409,
                                "UPLOAD_IN_PROGRESS",
                                "该产物正在上传或等待清理");
                        return new Reservation(existing, true);
                    }
                    int quota =
                            repository.update(
                                    "update ai_engineering_task set"
                                        + " reserved_bytes=reserved_bytes+?,reserved_files=reserved_files+1"
                                        + " where tenant_id=? and user_id=? and task_id=? and"
                                        + " state='RUNNING' and attempt=? and"
                                        + " cancel_requested=false and reserved_files<? and"
                                        + " reserved_bytes<=?",
                                    ticket.size(),
                                    ticket.tenantId(),
                                    ticket.userId(),
                                    ticket.taskId(),
                                    ticket.attempt(),
                                    properties.getMaxFiles(),
                                    properties.getMaxTaskBytes() - ticket.size());
                    require(quota == 1, 413, "UPLOAD_TOO_LARGE", "任务累计文件数或总字节数超过配额");
                    long id = IdWorker.getId();
                    String objectKey =
                            EngineeringObjectNamespace.key(principal.actor(), ticket.taskId(), id);
                    repository.update(
                            "insert into"
                                + " ai_engineering_artifact(artifact_id,tenant_id,user_id,task_id,attempt,artifact_type,group_id,relative_path,service,oss_id,object_key,sha256,size,mime_type,original_filename,status,validation_json,created_at)"
                                + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RESERVED',?,?)",
                            id,
                            ticket.tenantId(),
                            ticket.userId(),
                            ticket.taskId(),
                            ticket.attempt(),
                            ticket.artifactType().name(),
                            ticket.groupId(),
                            ticket.relativePath(),
                            properties.getOssConfigKey(),
                            id,
                            objectKey,
                            ticket.sha256(),
                            ticket.size(),
                            checked.mimeType(),
                            ticket.originalFilename(),
                            checked.validationJson(),
                            now());
                    return new Reservation(
                            EngineeringRepository.one(
                                    repository.query(
                                            AiEngineeringArtifact.class,
                                            "select * from ai_engineering_artifact where"
                                                + " tenant_id=? and user_id=? and artifact_id=?",
                                            ticket.tenantId(),
                                            ticket.userId(),
                                            id)),
                            false);
                });
    }

    private AiEngineeringArtifact findPath(AiEngineeringTask task, String path) {
        return EngineeringRepository.one(
                repository.query(
                        AiEngineeringArtifact.class,
                        "select * from ai_engineering_artifact where tenant_id=? and user_id=? and"
                            + " task_id=? and attempt=? and relative_path=?",
                        task.getTenantId(),
                        task.getUserId(),
                        task.getTaskId(),
                        task.getAttempt(),
                        path));
    }

    private void sameContent(AiEngineeringArtifact row, EngineeringUploadRequest request) {
        require(
                row.getArtifactType().equals(request.artifactType().name())
                        && Objects.equals(row.getGroupId(), request.groupId())
                        && row.getSha256().equalsIgnoreCase(request.sha256())
                        && row.getSize() == request.size(),
                409,
                "IDEMPOTENCY_CONFLICT",
                "同一产物路径已对应不同内容或类型");
    }

    private void abandon(AiEngineeringArtifact row) {
        repository.transaction(
                () -> {
                    repository.query(
                            AiEngineeringTask.class,
                            "select * from ai_engineering_task where tenant_id=? and user_id=? and"
                                + " task_id=? for update",
                            row.getTenantId(),
                            row.getUserId(),
                            row.getTaskId());
                    int changed =
                            repository.update(
                                    "update ai_engineering_artifact set status='ABANDONED' where"
                                        + " tenant_id=? and user_id=? and artifact_id=? and"
                                        + " status='RESERVED'",
                                    row.getTenantId(),
                                    row.getUserId(),
                                    row.getArtifactId());
                    if (changed == 1)
                        repository.update(
                                "update ai_engineering_task set"
                                    + " reserved_bytes=reserved_bytes-?,reserved_files=reserved_files-1"
                                    + " where tenant_id=? and user_id=? and task_id=? and"
                                    + " reserved_bytes>=? and reserved_files>0",
                                row.getSize(),
                                row.getTenantId(),
                                row.getUserId(),
                                row.getTaskId(),
                                row.getSize());
                    return null;
                });
    }

    /**
     * 有界回收未确认对象；以清理代数和五分钟租约 CAS 抢占，崩溃后可恢复过期 CLEANING。
     *
     * @return 本轮成功删除的未确认工程记录数，删除前后崩溃均可幂等重试
     * @throws org.springframework.dao.DataAccessException 元数据无法可靠更新时保留清理记录等待后续扫描
     */
    public int cleanupUnconfirmed() {
        var rows =
                repository.query(
                        AiEngineeringArtifact.class,
                        "select * from ai_engineering_artifact where (status in"
                            + " ('ABANDONED','CLEANING') and (cleanup_expires_at is null or"
                            + " cleanup_expires_at<=?)) or (status='RESERVED' and created_at<?)"
                            + " order by artifact_id limit 64",
                        now(),
                        now().minusMinutes(30));
        int count = 0;
        for (var row : rows)
            count +=
                    com.nexus.common.tenant.helper.TenantHelper.dynamic(
                            row.getTenantId(), () -> cleanupOne(row));
        return count;
    }

    private int cleanupOne(AiEngineeringArtifact row) {
        if ("RESERVED".equals(row.getStatus())) {
            var task = repository.task(row.getTenantId(), row.getUserId(), row.getTaskId());
            if (task != null
                    && "RUNNING".equals(task.getState())
                    && task.getLeaseExpiresAt() != null
                    && task.getLeaseExpiresAt().isAfter(now())
                    && task.getDeadlineAt() != null
                    && task.getDeadlineAt().isAfter(now())) return 0;
            abandon(row);
        }
        long previous = row.getCleanupGeneration() == null ? 0 : row.getCleanupGeneration();
        long generation = previous + 1;
        int claimed =
                repository.update(
                        "update ai_engineering_artifact set"
                            + " status='CLEANING',cleanup_generation=cleanup_generation+1,cleanup_expires_at=?"
                            + " where tenant_id=? and user_id=? and artifact_id=? and"
                            + " cleanup_generation=? and status in ('ABANDONED','CLEANING') and"
                            + " (cleanup_expires_at is null or cleanup_expires_at<=?)",
                        now().plusMinutes(5),
                        row.getTenantId(),
                        row.getUserId(),
                        row.getArtifactId(),
                        previous,
                        now());
        if (claimed != 1) return 0;
        try {
            storage.deleteUnconfirmed(stored(row));
            return repository.update(
                    "delete from ai_engineering_artifact where tenant_id=? and user_id=? and"
                        + " artifact_id=? and status='CLEANING' and cleanup_generation=?",
                    row.getTenantId(),
                    row.getUserId(),
                    row.getArtifactId(),
                    generation);
        } catch (Exception ignored) {
            repository.update(
                    "update ai_engineering_artifact set status='ABANDONED',cleanup_expires_at=?"
                        + " where tenant_id=? and user_id=? and artifact_id=? and status='CLEANING'"
                        + " and cleanup_generation=?",
                    now().plusMinutes(5),
                    row.getTenantId(),
                    row.getUserId(),
                    row.getArtifactId(),
                    generation);
            return 0;
        }
    }

    /** 将内部产物定位转换为私有存储端口参数，禁止直接输出给客户端。 */
    public static EngineeringArtifactStored stored(AiEngineeringArtifact row) {
        return new EngineeringArtifactStored(
                row.getOssId(),
                row.getService(),
                row.getObjectKey(),
                row.getSize(),
                row.getSha256());
    }

    private Uploaded uploaded(AiEngineeringArtifact row) {
        return new Uploaded(
                row.getArtifactId().toString(),
                row.getArtifactType(),
                row.getGroupId(),
                row.getRelativePath(),
                row.getSha256(),
                row.getSize(),
                row.getMimeType(),
                "/ai/engineering/artifacts/" + row.getArtifactId() + "/content");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 单次票据响应，已确认重传返回原 artifactId 且不重新发票据。
     *
     * @param uploadTicket 五分钟单次上传凭证，禁止记录日志
     * @param expiresAt UTC ISO-8601 到期时间
     * @param artifactId 服务端产物编号，对外使用十进制字符串
     * @param alreadyConfirmed 该键相同内容是否已经确认，可跳过重传
     */
    public record Ticket(
            String uploadTicket, String expiresAt, String artifactId, boolean alreadyConfirmed) {
        @Override
        public String toString() {
            return "Ticket[上传凭证已隐藏]";
        }
    }

    /**
     * 已确认产物回执，不含 OSS 定位和公共 URL。
     *
     * @param artifactId 服务端产物编号，对外使用十进制字符串
     * @param type 协议限定的产物类别
     * @param groupId OBJ、MTL 与纹理的同组标识
     * @param relativePath 组内规范相对路径，禁止盘符、URL、父目录和控制字符
     * @param sha256 服务器验真的完整 SHA-256
     * @param size 服务器验真的真实字节数
     * @param mimeType 服务器格式检测所得媒体类型
     * @param contentPath 每次读取仍需认证授权的内容接口
     */
    public record Uploaded(
            String artifactId,
            String type,
            String groupId,
            String relativePath,
            String sha256,
            long size,
            String mimeType,
            String contentPath) {}

    /**
     * Redis 内部绑定负载，不保存原始租约或设备凭证。
     *
     * @param deviceId 当前用户显式绑定的设备编号
     * @param tenantId 保留文本前导零的租户标识
     * @param userId 服务端认证的用户编号
     * @param taskId 服务端任务编号，对外使用十进制字符串
     * @param attempt 本次领取尝试编号，旧尝试禁止写入
     * @param leaseHash 只供服务端比较的租约摘要
     * @param expiresAt UTC ISO-8601 到期时间
     * @param artifactType 协议限定的产物类别
     * @param groupId OBJ、MTL 与纹理的同组标识
     * @param relativePath 组内规范相对路径，禁止盘符、URL、父目录和控制字符
     * @param sha256 服务器验真的完整 SHA-256
     * @param size 服务器验真的真实字节数
     * @param mimeType 服务器格式检测所得媒体类型
     * @param originalFilename 仅用于安全下载显示的文件名，不是存储键
     */
    public record TicketData(
            Long deviceId,
            String tenantId,
            Long userId,
            Long taskId,
            int attempt,
            String leaseHash,
            String expiresAt,
            EngineeringArtifactType artifactType,
            String groupId,
            String relativePath,
            String sha256,
            long size,
            String mimeType,
            String originalFilename) {
        /** 重建不含原始租约的验证参数，只用于文件格式与幂等核对。 */
        public EngineeringUploadRequest request() {
            return new EngineeringUploadRequest(
                    attempt,
                    null,
                    0,
                    artifactType,
                    groupId,
                    relativePath,
                    sha256,
                    size,
                    mimeType,
                    originalFilename);
        }
    }

    private record Reservation(AiEngineeringArtifact artifact, boolean alreadyConfirmed) {}
}
