package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.security.EngineeringAccess;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.engineering.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 工程私有清单、授权读取与完成判定；连接器不能自报成功或可投产状态。 */
@Service
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringArtifactService {
    private final EngineeringRepository repository;
    private final EngineeringTaskService tasks;
    private final EngineeringDeviceService devices;
    private final EngineeringAccess access;
    private final EngineeringStoragePort storage;

    /**
     * 读取本人任务当前尝试已确认清单，失败或取消任务保留诊断文件。
     *
     * @param actor 当前服务端登录身份，读取时重查权限
     * @param taskId 本人任务编号
     * @return 不含存储定位和公共 URL 的有界清单
     * @throws EngineeringException 无查询权限、跨 owner、非法组内引用或清单超限
     */
    public EngineeringArtifactManifest manifest(EngineeringActor actor, Long taskId) {
        access.requireActor(actor, "ai:engineering:query", false);
        var task = tasks.ownedTask(actor, taskId);
        var rows = confirmed(task);
        return buildManifest(task, rows);
    }

    /**
     * 执行设备读取部分清单用于上传丢响应确认；未上传材质的引用在 complete 时统一校验。
     *
     * @param principal 当前认证设备
     * @param taskId 仅限该设备本人任务
     * @return 当前已确认文件，未确认对象永不出现
     * @throws EngineeringException 授权失效、任务不属于当前设备或清单无效
     */
    public EngineeringArtifactManifest deviceManifest(
            EngineeringDevicePrincipal principal, Long taskId) {
        devices.requireDevice(principal);
        access.requireActor(principal.actor(), "ai:engineering:query", false);
        var task = tasks.ownedTask(principal.actor(), taskId);
        require(
                Objects.equals(task.getDeviceId(), principal.deviceId()),
                403,
                "DEVICE_MISMATCH",
                "任务不属于当前设备");
        return buildManifest(task, confirmed(task));
    }

    /**
     * 从当前尝试已确认文件和验真的 DRC 报告计算真实终态；连接器自报成功文字不参与判定。
     *
     * @param principal 当前设备身份，服务端会再次复核实时授权
     * @param taskId 本人本设备任务编号
     * @param lease 当前尝试、原租约及预期修订号
     * @return 服务端计算并持久化的 SUCCEEDED/PARTIAL_SUCCESS/FAILED 任务；canManufacture 恒 false
     * @throws EngineeringException 旧租约、旧修订号、取消竞争或 OBJ/MTL 同组引用无效
     */
    public EngineeringTaskVo complete(
            EngineeringDevicePrincipal principal, Long taskId, EngineeringLease lease) {
        return tasks.finish(
                principal,
                taskId,
                lease,
                task -> {
                    var rows = confirmed(task);
                    var manifest = buildManifest(task, rows);
                    var types = EnumSet.noneOf(EngineeringArtifactType.class);
                    rows.forEach(
                            a -> types.add(EngineeringArtifactType.valueOf(a.getArtifactType())));
                    var action = EngineeringAction.valueOf(task.getAction());
                    String error = task.getErrorCode();
                    if (error != null
                            && !Set.of("EXPORT_FAILED", "DRC_UNAVAILABLE").contains(error))
                        return completion(EngineeringTaskState.FAILED, error, "本地工程动作失败，已保留可用诊断产物");
                    if (action == EngineeringAction.CHECK_CONNECTION
                            || action == EngineeringAction.OPEN_PROJECT)
                        return completion(
                                error == null
                                        ? EngineeringTaskState.SUCCEEDED
                                        : EngineeringTaskState.PARTIAL_SUCCESS,
                                error,
                                error == null ? null : "工程动作完成但存在导出异常");
                    if (action == EngineeringAction.RUN_DRC) {
                        if (!types.contains(EngineeringArtifactType.PCB_DRC_REPORT))
                            return completion(
                                    EngineeringTaskState.FAILED, "DRC_UNAVAILABLE", "未收到真实DRC报告");
                        return "PASS".equals(manifest.drc().status()) && error == null
                                ? completion(EngineeringTaskState.SUCCEEDED, null, null)
                                : completion(
                                        EngineeringTaskState.PARTIAL_SUCCESS,
                                        "DRC_UNAVAILABLE",
                                        "DRC存在违规或不可用，不能投产");
                    }
                    boolean source = types.contains(EngineeringArtifactType.PCB_SOURCE);
                    boolean preview =
                            types.contains(EngineeringArtifactType.PCB_PREVIEW_2D)
                                    || types.contains(EngineeringArtifactType.PCB_MODEL_OBJ);
                    if (!source || !preview)
                        return completion(
                                EngineeringTaskState.FAILED,
                                "EXPORT_FAILED",
                                "未生成源工程或有效预览，已保留现有产物");
                    boolean all =
                            manifest.missingTypes().isEmpty()
                                    && "PASS".equals(manifest.drc().status())
                                    && error == null
                                    && manifest.modelGroups().stream()
                                            .allMatch(group -> group.status().equals("READY"));
                    return all
                            ? completion(EngineeringTaskState.SUCCEEDED, null, null)
                            : completion(
                                    EngineeringTaskState.PARTIAL_SUCCESS,
                                    "EXPORT_FAILED",
                                    "草稿已归档，产物缺失或DRC存在违规，须工程师审查");
                });
    }

    private EngineeringTaskService.Completion completion(
            EngineeringTaskState state, String code, String message) {
        return new EngineeringTaskService.Completion(state, code, message);
    }

    private EngineeringArtifactManifest buildManifest(
            AiEngineeringTask task, List<AiEngineeringArtifact> rows) {
        var types = EnumSet.noneOf(EngineeringArtifactType.class);
        var files = new ArrayList<EngineeringArtifactManifest.Artifact>();
        for (var row : rows) {
            var type = EngineeringArtifactType.valueOf(row.getArtifactType());
            types.add(type);
            files.add(
                    new EngineeringArtifactManifest.Artifact(
                            row.getArtifactId().toString(),
                            type,
                            row.getGroupId(),
                            row.getRelativePath(),
                            row.getSha256(),
                            row.getSize(),
                            row.getMimeType(),
                            "/ai/engineering/artifacts/" + row.getArtifactId() + "/content"));
        }
        var missing = new ArrayList<>(expected(EngineeringAction.valueOf(task.getAction())));
        missing.removeAll(types);
        var result =
                new EngineeringArtifactManifest(
                        EngineeringDeviceService.PROTOCOL,
                        task.getTaskId().toString(),
                        task.getRevision(),
                        task.getState(),
                        drc(rows),
                        files,
                        missing,
                        false,
                        modelGroups(rows));
        require(
                EngineeringJson.write(result).getBytes(StandardCharsets.UTF_8).length <= 128 * 1024,
                413,
                "MANIFEST_TOO_LARGE",
                "产物清单超过限制");
        return result;
    }

    private Set<EngineeringArtifactType> expected(EngineeringAction action) {
        return switch (action) {
            case CHECK_CONNECTION, OPEN_PROJECT -> EnumSet.noneOf(EngineeringArtifactType.class);
            case RUN_DRC -> EnumSet.of(EngineeringArtifactType.PCB_DRC_REPORT);
            default ->
                    EnumSet.of(
                            EngineeringArtifactType.PCB_SOURCE,
                            EngineeringArtifactType.PCB_PREVIEW_2D,
                            EngineeringArtifactType.PCB_BOM,
                            EngineeringArtifactType.PCB_NETLIST,
                            EngineeringArtifactType.PCB_DRC_REPORT,
                            EngineeringArtifactType.PCB_MODEL_OBJ);
        };
    }

    private EngineeringArtifactManifest.Drc drc(List<AiEngineeringArtifact> rows) {
        int errors = 0, warnings = 0, count = 0;
        boolean unavailable = false, violations = false, truncated = false, notRun = false;
        for (var row : rows)
            if (row.getArtifactType().equals("PCB_DRC_REPORT")) {
                var report = EngineeringJson.tree(row.getValidationJson()).path("drc");
                String status = report.path("status").asText("UNAVAILABLE");
                count++;
                require(
                        Set.of("PASS", "VIOLATIONS", "UNAVAILABLE", "NOT_RUN").contains(status),
                        422,
                        "ARTIFACT_FORMAT_INVALID",
                        "DRC归档摘要无效");
                errors = Math.addExact(errors, report.path("errorCount").asInt());
                warnings = Math.addExact(warnings, report.path("warningCount").asInt());
                unavailable |= status.equals("UNAVAILABLE");
                notRun |= status.equals("NOT_RUN");
                violations |= status.equals("VIOLATIONS");
                truncated |= report.path("truncated").asBoolean();
            }
        String status =
                count == 0 || unavailable
                        ? "UNAVAILABLE"
                        : violations ? "VIOLATIONS" : notRun ? "NOT_RUN" : "PASS";
        return new EngineeringArtifactManifest.Drc(status, errors, warnings, truncated);
    }

    private List<AiEngineeringArtifact> confirmed(AiEngineeringTask task) {
        var rows =
                repository.query(
                        AiEngineeringArtifact.class,
                        "select * from ai_engineering_artifact where tenant_id=? and user_id=? and"
                            + " task_id=? and attempt=? and status='CONFIRMED' order by artifact_id"
                            + " limit 65",
                        task.getTenantId(),
                        task.getUserId(),
                        task.getTaskId(),
                        task.getAttempt());
        require(rows.size() <= 64, 413, "MANIFEST_TOO_LARGE", "任务文件数量超限");
        var paths = new HashSet<String>();
        for (var row : rows) {
            EngineeringPaths.validate(row.getRelativePath());
            require(
                    paths.add(row.getRelativePath()),
                    422,
                    "ARTIFACT_REFERENCE_INVALID",
                    "任务清单存在重复规范路径");
            require(
                    row.getSha256() != null
                            && row.getSha256().matches("[a-f0-9]{64}")
                            && row.getSize() != null
                            && row.getSize() > 0,
                    422,
                    "ARTIFACT_FORMAT_INVALID",
                    "归档文件摘要或大小无效");
        }
        return rows;
    }

    private List<EngineeringArtifactManifest.ModelGroup> modelGroups(
            List<AiEngineeringArtifact> rows) {
        var byPath = new HashMap<String, AiEngineeringArtifact>();
        var groups = new TreeMap<String, List<AiEngineeringArtifact>>();
        for (var row : rows)
            if (Set.of("PCB_MODEL_OBJ", "PCB_MODEL_MTL", "PCB_MODEL_TEXTURE")
                    .contains(row.getArtifactType())) {
                require(row.getGroupId() != null, 422, "ARTIFACT_REFERENCE_INVALID", "模型缺少组标识");
                groups.computeIfAbsent(row.getGroupId(), key -> new ArrayList<>()).add(row);
                byPath.put(row.getGroupId() + "/" + row.getRelativePath(), row);
            }
        var result = new ArrayList<EngineeringArtifactManifest.ModelGroup>();
        int diagnosticBudget = 128;
        for (var entry : groups.entrySet()) {
            var missing = new TreeSet<String>();
            boolean geometry = false, material = false;
            for (var row : entry.getValue()) {
                geometry |= row.getArtifactType().equals("PCB_MODEL_OBJ");
                material |= row.getArtifactType().equals("PCB_MODEL_MTL");
                if (!Set.of("PCB_MODEL_OBJ", "PCB_MODEL_MTL").contains(row.getArtifactType()))
                    continue;
                for (var reference :
                        EngineeringJson.tree(row.getValidationJson()).path("references")) {
                    // 非法路径仍失败；缺少本组已确认引用仅降级，不能让PNG和源文件一并不可读。
                    String path = EngineeringPaths.validate(reference.path("path").asText());
                    String kind = reference.path("kind").asText();
                    require(
                            Set.of("MTL", "TEXTURE").contains(kind),
                            422,
                            "ARTIFACT_REFERENCE_INVALID",
                            "模型引用类别无效");
                    var target = byPath.get(entry.getKey() + "/" + path);
                    String expected = kind.equals("MTL") ? "PCB_MODEL_MTL" : "PCB_MODEL_TEXTURE";
                    if (target == null || !target.getArtifactType().equals(expected))
                        missing.add(path);
                }
            }
            String status =
                    !geometry
                            ? "MISSING_GEOMETRY"
                            : !missing.isEmpty() ? "DEGRADED" : !material ? "NO_MATERIAL" : "READY";
            var shown = missing.stream().limit(diagnosticBudget).toList();
            diagnosticBudget -= shown.size();
            result.add(
                    new EngineeringArtifactManifest.ModelGroup(
                            entry.getKey(),
                            status,
                            !status.equals("READY"),
                            shown,
                            shown.size() < missing.size()));
        }
        return result;
    }

    private AiEngineeringArtifact authorized(EngineeringActor actor, Long artifactId) {
        access.requireActor(actor, "ai:engineering:download", false);
        var row =
                EngineeringRepository.one(
                        repository.query(
                                AiEngineeringArtifact.class,
                                "select * from ai_engineering_artifact where tenant_id=? and"
                                    + " user_id=? and artifact_id=? and status='CONFIRMED'",
                                actor.tenantId(),
                                actor.userId(),
                                artifactId));
        require(row != null, 404, "ARTIFACT_NOT_FOUND", "产物不存在或不属于当前用户");
        var task = tasks.ownedTask(actor, row.getTaskId());
        require(
                Objects.equals(task.getAttempt(), row.getAttempt()),
                404,
                "ARTIFACT_NOT_FOUND",
                "该产物不是当前有效尝试");
        return row;
    }

    /**
     * 每次请求复核 owner 和下载权限，再代理读取私有文件流。
     *
     * @param actor 服务端认证的当前用户
     * @param artifactId 本人任务已确认产物编号
     * @return 需要调用方关闭的私有文件流及验真媒体信息
     * @throws EngineeringException 产物不属于当前 owner、权限失效或不是当前有效尝试
     * @throws IOException 私有对象不存在或读取失败
     */
    public Content open(EngineeringActor actor, Long artifactId) throws IOException {
        var row = authorized(actor, artifactId);
        String mime = row.getMimeType();
        boolean inline =
                Set.of("image/png", "image/jpeg").contains(mime)
                        && Set.of("PCB_PREVIEW_2D", "PCB_MODEL_TEXTURE")
                                .contains(row.getArtifactType());
        return new Content(
                storage.read(EngineeringArtifactUploadService.stored(row)),
                mime,
                row.getSize(),
                row.getOriginalFilename(),
                inline);
    }

    /** 返回最多一万行的规范 BOM/DRC 数据，原文件仍可经 content 接口下载校验摘要。 */
    public Object report(EngineeringActor actor, Long artifactId) {
        var row = authorized(actor, artifactId);
        require(
                Set.of("PCB_BOM", "PCB_DRC_REPORT").contains(row.getArtifactType()),
                422,
                "ARTIFACT_TYPE_UNSUPPORTED",
                "该产物不是结构化报告");
        var validation = EngineeringJson.tree(row.getValidationJson());
        return validation.path(row.getArtifactType().equals("PCB_BOM") ? "bom" : "drc");
    }

    /**
     * 已授权的私有文件流，仅 Controller 可用于响应，调用方必须关闭。
     *
     * @param input 调用方必须关闭的已授权私有文件流
     * @param mimeType 服务器格式检测所得媒体类型
     * @param size 服务器验真的真实字节数
     * @param originalFilename 仅用于安全下载显示的文件名，不是存储键
     * @param inline 是否允许以内联图片展示
     */
    public record Content(
            InputStream input,
            String mimeType,
            long size,
            String originalFilename,
            boolean inline) {}
}
