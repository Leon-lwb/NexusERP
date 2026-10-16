package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.ai.engineering.security.*;
import com.nexus.common.ai.domain.entity.AiEngineeringDevice;
import com.nexus.common.ai.engineering.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** 工程设备配对、旋转、撤销及心跳；数据库仅存凭证摘要。 */
@Service
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringDeviceService {
    public static final String PROTOCOL = "nexus-engineering/1";
    private final EngineeringRepository repository;
    private final EngineeringEphemeralStore ephemeral;
    private final EngineeringAccess access;
    private final EngineeringProperties properties;
    private final Clock clock;

    /**
     * 创建固定所有者及允许动作的五分钟配对码；Redis 仅存摘要键，兑换原子消费。
     *
     * @param actor 服务端认证的本人身份，实时复核设备管理权限
     * @param capabilities 允许当前设备使用的动作集合，空值采用已定义动作集合
     * @return 仅本次返回的配对码、UTC 到期时间和协议版本
     * @throws EngineeringException 无有效身份、用户/租户停用或无设备权限
     */
    public PairingCode createPairingCode(
            EngineeringActor actor, Set<EngineeringAction> capabilities) {
        var fresh = access.requireActor(actor, "ai:engineering:device", false);
        var allowed =
                capabilities == null || capabilities.isEmpty()
                        ? Set.of(EngineeringAction.values())
                        : Set.copyOf(capabilities);
        String code = EngineeringSecrets.token();
        ephemeral.put(
                "pair",
                code,
                EngineeringJson.write(
                        new PairingPayload(fresh.tenantId(), fresh.userId(), allowed)),
                Duration.ofMinutes(5));
        return new PairingCode(code, clock.instant().plusSeconds(300).toString(), PROTOCOL);
    }

    /**
     * 原子消费配对码并创建设备；正文不能改变 tenantId、userId 或允许能力。
     *
     * @param request 协议版本、单次配对码与安全设备名称
     * @return 设备编号与新访问/刷新凭证，数据库仅存摘要
     * @throws EngineeringException 未知协议、配对码过期或已消费、owner 失效、设备名称无效
     */
    public Credentials pair(PairRequest request) {
        require(
                request != null && PROTOCOL.equals(request.protocolVersion()),
                422,
                "PROTOCOL_UNSUPPORTED",
                "不支持该连接器协议版本");
        require(
                request.pairingCode() != null && request.pairingCode().length() <= 128,
                401,
                "PAIRING_INVALID",
                "配对码无效或已过期");
        String body = ephemeral.consume("pair", request.pairingCode());
        require(body != null, 401, "PAIRING_INVALID", "配对码无效、已使用或已过期");
        var pair = EngineeringJson.read(body, PairingPayload.class);
        var actor =
                access.requireActor(
                        new EngineeringActor(pair.tenantId(), pair.userId(), Set.of()),
                        "ai:engineering:device",
                        false);
        var owner = access.owner(actor);
        String token = EngineeringSecrets.token(), refresh = EngineeringSecrets.token();
        long id = IdWorker.getId();
        var now = clock.instant();
        String name = request.deviceName() == null ? "本地工程设备" : request.deviceName().strip();
        require(!name.isEmpty() && name.length() <= 120, 422, "INVALID_REQUEST", "设备名称长度无效");
        repository.update(
                "insert into"
                    + " ai_engineering_device(device_id,tenant_id,user_id,device_name,status,access_hash,refresh_hash,generation,access_expires_at,refresh_expires_at,credential_fingerprint,allowed_capabilities,created_at)"
                    + " values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                actor.userId(),
                name,
                "ACTIVE",
                EngineeringSecrets.hash(token),
                EngineeringSecrets.hash(refresh),
                1,
                utc(now.plus(properties.getAccessDuration())),
                utc(now.plus(properties.getRefreshDuration())),
                owner.credentialFingerprint(),
                EngineeringJson.write(pair.capabilities()),
                utc(now));
        return credentials(id, token, refresh, now);
    }

    /**
     * 以单条 CAS 原子旋转访问/刷新摘要及代数；刷新响应丢失后要求重新配对。
     *
     * @param raw 单次刷新凭证原文，仅用于摘要比较，禁止输出到日志
     * @return 新访问凭证、新刷新凭证和 UTC 到期时间
     * @throws EngineeringException 凭证已使用或到期、设备解绑、密码状态变化、owner/工具授权失效
     */
    public Credentials refresh(String raw) {
        require(raw != null && raw.length() <= 128, 401, "DEVICE_REVOKED", "刷新凭证无效，请重新配对");
        var device = repository.deviceByHash(EngineeringSecrets.hash(raw), true);
        var actor = validateOwner(device);
        require(
                device.getRefreshExpiresAt() != null && device.getRefreshExpiresAt().isAfter(now()),
                401,
                "DEVICE_REVOKED",
                "刷新凭证已过期，请重新配对");
        String token = EngineeringSecrets.token(), refresh = EngineeringSecrets.token();
        var now = clock.instant();
        int changed =
                repository.update(
                        "update ai_engineering_device set"
                            + " access_hash=?,refresh_hash=?,generation=generation+1,access_expires_at=?,refresh_expires_at=?"
                            + " where device_id=? and tenant_id=? and user_id=? and status='ACTIVE'"
                            + " and generation=? and refresh_hash=?",
                        EngineeringSecrets.hash(token),
                        EngineeringSecrets.hash(refresh),
                        utc(now.plus(properties.getAccessDuration())),
                        utc(now.plus(properties.getRefreshDuration())),
                        device.getDeviceId(),
                        actor.tenantId(),
                        actor.userId(),
                        device.getGeneration(),
                        EngineeringSecrets.hash(raw));
        require(changed == 1, 401, "DEVICE_REVOKED", "刷新凭证已使用，请重新配对");
        return credentials(device.getDeviceId(), token, refresh, now);
    }

    /**
     * 根据访问摘要还原设备，并实时复核设备、租户、用户、密码状态及工程权限。
     *
     * @param raw X-Nexus-Device-Token 的访问凭证，不能从正文读取
     * @return 不含任何凭证原文的已认证设备身份
     * @throws EngineeringException 未知或过期访问凭证、解绑、认证状态变化或当前授权失效
     */
    public EngineeringDevicePrincipal authenticate(String raw) {
        require(raw != null && raw.length() <= 128, 401, "DEVICE_REVOKED", "缺少有效设备访问凭证");
        var device = repository.deviceByHash(EngineeringSecrets.hash(raw), false);
        var actor = validateOwner(device);
        require(
                device.getAccessExpiresAt() != null && device.getAccessExpiresAt().isAfter(now()),
                401,
                "DEVICE_REVOKED",
                "设备访问凭证已过期");
        return new EngineeringDevicePrincipal(device.getDeviceId(), actor, device.getGeneration());
    }

    /**
     * 再次核对设备代数、访问到期及实时 owner 状态，用于长上传之后的确认。
     *
     * @param principal 请求开始时认证的设备身份
     * @return 当前有效的内部设备记录，不得直接序列化为接口出参
     * @throws EngineeringException 代数旋转、访问到期、解绑或 owner 权限失效
     */
    public AiEngineeringDevice requireDevice(EngineeringDevicePrincipal principal) {
        var actor = principal.actor();
        var device = repository.device(actor.tenantId(), actor.userId(), principal.deviceId());
        validateOwner(device);
        require(
                device.getAccessExpiresAt() != null && device.getAccessExpiresAt().isAfter(now()),
                401,
                "DEVICE_REVOKED",
                "设备访问凭证已过期");
        require(
                device.getGeneration() == principal.generation(),
                401,
                "DEVICE_REVOKED",
                "设备凭证已旋转或撤销");
        return device;
    }

    private EngineeringActor validateOwner(AiEngineeringDevice device) {
        require(
                device != null && "ACTIVE".equals(device.getStatus()),
                401,
                "DEVICE_REVOKED",
                "设备已失效，请重新配对");
        var actor =
                access.requireActor(
                        new EngineeringActor(device.getTenantId(), device.getUserId(), Set.of()),
                        "ai:engineering:task",
                        true);
        access.requireActor(actor, "ai:engineering:device", false);
        require(
                Objects.equals(
                        device.getCredentialFingerprint(),
                        access.owner(actor).credentialFingerprint()),
                403,
                "OWNER_REVOKED",
                "认证状态已变化，请重新配对");
        return actor;
    }

    /**
     * 列举本人设备的安全视图；不得输出凭证摘要、内部密码状态和刷新凭证。
     *
     * @param actor 当前服务端身份
     * @return 仅当前 owner 的设备视图及实际心跳能力
     * @throws EngineeringException 用户、租户或设备管理权限失效
     */
    public List<DeviceView> list(EngineeringActor actor) {
        access.requireActor(actor, "ai:engineering:device", false);
        return repository
                .query(
                        AiEngineeringDevice.class,
                        "select * from ai_engineering_device where tenant_id=? and user_id=? order"
                            + " by created_at desc",
                        actor.tenantId(),
                        actor.userId())
                .stream()
                .map(this::view)
                .toList();
    }

    /**
     * 按精确 owner 解绑设备，立即撤销访问和刷新摘要并递增代数。
     *
     * @param actor 当前服务端用户身份
     * @param id 本人设备编号
     * @throws EngineeringException 无设备权限或设备不属于当前用户
     */
    public void revoke(EngineeringActor actor, Long id) {
        access.requireActor(actor, "ai:engineering:device", false);
        int changed =
                repository.update(
                        "update ai_engineering_device set"
                            + " status='REVOKED',access_hash=null,refresh_hash=null,generation=generation+1"
                            + " where tenant_id=? and user_id=? and device_id=?",
                        actor.tenantId(),
                        actor.userId(),
                        id);
        require(changed == 1, 404, "DEVICE_NOT_FOUND", "设备不存在或不属于当前用户");
    }

    /**
     * 连接器撤销当前设备，不能指定其他设备；解绑与凭证旋转通过代数 CAS 竞争。
     *
     * @param principal 过滤器认证的当前设备身份
     * @throws EngineeringException 设备已解绑、凭证已旋转/到期或 owner 失效
     */
    public void unpair(EngineeringDevicePrincipal principal) {
        repository.transaction(
                () -> {
                    var actor = principal.actor();
                    repository.query(
                            AiEngineeringDevice.class,
                            "select * from ai_engineering_device where tenant_id=? and user_id=?"
                                + " and device_id=? for update",
                            actor.tenantId(),
                            actor.userId(),
                            principal.deviceId());
                    requireDevice(principal);
                    int changed =
                            repository.update(
                                    "update ai_engineering_device set"
                                        + " status='REVOKED',access_hash=null,refresh_hash=null,generation=generation+1"
                                        + " where tenant_id=? and user_id=? and device_id=? and"
                                        + " generation=? and status='ACTIVE'",
                                    actor.tenantId(),
                                    actor.userId(),
                                    principal.deviceId(),
                                    principal.generation());
                    require(changed == 1, 401, "DEVICE_REVOKED", "当前设备凭证已失效");
                    return null;
                });
    }

    /**
     * 只更新当前设备的实际能力、版本与诊断，不修改所有者或任务归属。
     *
     * @param principal 当前已认证设备
     * @param request 协议版本、能力、模板摘要及真实编辑器/传输诊断
     * @return 当前设备安全视图
     * @throws EngineeringException 设备授权失效、未知协议或能力元数据超限
     */
    public DeviceView heartbeat(EngineeringDevicePrincipal principal, Heartbeat request) {
        requireDevice(principal);
        require(
                request != null && PROTOCOL.equals(request.protocolVersion()),
                422,
                "PROTOCOL_UNSUPPORTED",
                "不支持该连接器协议版本");
        String json = EngineeringJson.write(request);
        require(json.length() <= 16384, 422, "INVALID_REQUEST", "设备能力描述超限");
        var actor = principal.actor();
        repository.update(
                "update ai_engineering_device set capabilities=?,last_heartbeat=? where tenant_id=?"
                    + " and user_id=? and device_id=? and status='ACTIVE' and generation=?",
                json,
                now(),
                actor.tenantId(),
                actor.userId(),
                principal.deviceId(),
                principal.generation());
        return view(repository.device(actor.tenantId(), actor.userId(), principal.deviceId()));
    }

    private DeviceView view(AiEngineeringDevice d) {
        return new DeviceView(
                d.getDeviceId().toString(),
                d.getDeviceName(),
                d.getStatus(),
                d.getCapabilities() == null ? null : EngineeringJson.tree(d.getCapabilities()),
                d.getLastHeartbeat() == null
                        ? null
                        : d.getLastHeartbeat().toInstant(ZoneOffset.UTC).toString(),
                d.getBusyTaskId() == null ? null : d.getBusyTaskId().toString());
    }

    private Credentials credentials(Long id, String token, String refresh, Instant now) {
        return new Credentials(
                id.toString(),
                token,
                now.plus(properties.getAccessDuration()).toString(),
                refresh,
                now.plus(properties.getRefreshDuration()).toString(),
                PROTOCOL);
    }

    private LocalDateTime now() {
        return utc(clock.instant());
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * 服务端配对负载，Redis 中不含配对码原文。
     *
     * @param tenantId 保留文本前导零的租户标识
     * @param userId 服务端认证的用户编号
     * @param capabilities 设备如实报告或配对允许的能力集合
     */
    public record PairingPayload(
            String tenantId, Long userId, Set<EngineeringAction> capabilities) {}

    /**
     * 配对码响应，仅生成时返回一次。
     *
     * @param pairingCode 五分钟有效的单次随机配对码
     * @param expiresAt UTC ISO-8601 到期时间
     * @param protocolVersion 固定协议主版本 nexus-engineering/1
     */
    public record PairingCode(String pairingCode, String expiresAt, String protocolVersion) {
        @Override
        public String toString() {
            return "PairingCode[已隐藏]";
        }
    }

    /**
     * 连接器兑换参数。
     *
     * @param protocolVersion 固定协议主版本 nexus-engineering/1
     * @param pairingCode 五分钟有效的单次随机配对码
     * @param deviceName 供当前用户识别的设备名称
     */
    public record PairRequest(String protocolVersion, String pairingCode, String deviceName) {
        @Override
        public String toString() {
            return "PairRequest[已隐藏]";
        }
    }

    /**
     * 刷新和配对响应，凭证仅通过专用响应返回。
     *
     * @param deviceId 当前用户显式绑定的设备编号
     * @param deviceToken 短期访问凭证，仅经专用响应返回一次
     * @param deviceTokenExpiresAt UTC ISO-8601 访问凭证到期时间
     * @param refreshToken 单次旋转刷新凭证，客户端必须安全保存
     * @param refreshTokenExpiresAt UTC ISO-8601 刷新凭证到期时间
     * @param protocolVersion 固定协议主版本 nexus-engineering/1
     */
    public record Credentials(
            String deviceId,
            String deviceToken,
            String deviceTokenExpiresAt,
            String refreshToken,
            String refreshTokenExpiresAt,
            String protocolVersion) {
        @Override
        public String toString() {
            return "Credentials[已隐藏]";
        }
    }

    /**
     * 设备心跳，模板摘要和实际导出能力由连接器如实上报。
     *
     * @param protocolVersion 固定协议主版本 nexus-engineering/1
     * @param connectorVersion 连接器实际版本
     * @param cliVersion 本地 CLI 实际版本
     * @param editorVersion 本地编辑器实际版本
     * @param capabilities 设备如实报告或配对允许的能力集合
     * @param templateHashes 本地已审模板的实际摘要集合
     * @param editorAvailable 本地编辑器当前是否连接，不等于设备在线与否
     * @param transportMode 实际传输模式，不据此伪称官方 MCP 可用
     * @param diagnosticCode 不包含敏感数据的诊断类别
     * @param diagnosticMessage 不包含路径和凭证的中文诊断摘要
     */
    public record Heartbeat(
            String protocolVersion,
            String connectorVersion,
            String cliVersion,
            String editorVersion,
            Set<String> capabilities,
            Map<String, String> templateHashes,
            Boolean editorAvailable,
            String transportMode,
            String diagnosticCode,
            String diagnosticMessage) {
        /** 兼容未上报诊断字段的首版设备，缺失值表示未知而非官方MCP已可用。 */
        public Heartbeat(
                String protocolVersion,
                String connectorVersion,
                String cliVersion,
                String editorVersion,
                Set<String> capabilities,
                Map<String, String> templateHashes,
                Boolean editorAvailable) {
            this(
                    protocolVersion,
                    connectorVersion,
                    cliVersion,
                    editorVersion,
                    capabilities,
                    templateHashes,
                    editorAvailable,
                    null,
                    null,
                    null);
        }
    }

    /**
     * 面向浏览器的安全设备信息。
     *
     * @param deviceId 当前用户显式绑定的设备编号
     * @param deviceName 供当前用户识别的设备名称
     * @param status 明确的业务状态，不从空报告推断通过
     * @param capabilities 设备如实报告或配对允许的能力集合
     * @param lastHeartbeat UTC 最近心跳时间
     * @param busyTaskId 设备当前任务编号，空值表示空闲
     */
    public record DeviceView(
            String deviceId,
            String deviceName,
            String status,
            Object capabilities,
            String lastHeartbeat,
            String busyTaskId) {}
}
