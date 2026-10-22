package com.nexus.system.engineering;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiEngineeringArtifactMapper;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.enums.AccessPolicyType;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.system.domain.SysOss;
import com.nexus.system.mapper.SysOssMapper;
import com.nexus.system.service.ISysOssConfigService;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Objects;
import java.util.Set;

/** 工程私桶适配器，sys_oss 元数据仅作内部定位，不对外生成对象 URL。 */
@Component
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class SysEngineeringStorageAdapter implements EngineeringStoragePort {
    private final SysOssMapper mapper;
    private final AiEngineeringArtifactMapper artifacts;
    private final String configKey;
    private final ISysOssConfigService ossConfigs;

    /**
     * 注入工程私桶及 master 元数据契约，禁止回落默认 OSS。
     *
     * @param mapper 内部 sys_oss 元数据映射器
     * @param artifacts 工程产物映射器，用于证明 RESERVED/CONFIRMED/CLEANING 状态
     * @param configKey 显式配置的专用私桶键
     * @param ossConfigs 在私桶校验前从主库初始化可信OSS配置
     */
    public SysEngineeringStorageAdapter(
            SysOssMapper mapper,
            AiEngineeringArtifactMapper artifacts,
            @Value("${nexus.ai.engineering.oss-config-key:}") String configKey,
            ISysOssConfigService ossConfigs) {
        this.mapper = mapper;
        this.artifacts = artifacts;
        this.configKey = configKey;
        this.ossConfigs = ossConfigs;
    }

    /**
     * 启用工程功能时立即校验私桶配置，关闭功能时不会装配此适配器。
     *
     * @throws IllegalStateException 未指定配置或配置为公共桶时拒绝启动
     */
    @PostConstruct
    public void verifyConfiguration() {
        // ApplicationRunner runs after @PostConstruct; a new/cleared Redis cache must not block boot.
        ossConfigs.init();
        client();
    }

    private OssClient client() {
        if (configKey == null || configKey.isBlank())
            throw new IllegalStateException("工程功能必须指定私有ossConfigKey");
        var client = OssFactory.instance(configKey);
        if (client.getAccessPolicy() != AccessPolicyType.PRIVATE)
            throw new IllegalStateException("工程产物只允许使用私有OSS配置");
        return client;
    }

    /**
     * 在任务事务外写入私桶；先证明产物处于预留状态，所有对象键均由服务器生成。
     *
     * @param write 已验真文件、服务端 owner 和任务/产物编号
     * @return 仅供服务端保存的私有定位、大小及摘要
     * @throws IOException 暂存文件不存在、不是普通文件或真实大小发生变化
     * @throws IllegalStateException 私桶配置或预留状态证明无效
     */
    @Override
    public EngineeringArtifactStored write(EngineeringArtifactWrite write) throws IOException {
        var client = client();
        String key =
                EngineeringObjectNamespace.key(write.actor(), write.taskId(), write.artifactId());
        if (!Files.isRegularFile(write.stagedPath(), LinkOption.NOFOLLOW_LINKS)
                || Files.size(write.stagedPath()) != write.size())
            throw new IOException("已验证暂存文件不存在或大小变化");
        requireArtifact(
                new EngineeringArtifactStored(
                        write.artifactId(), configKey, key, write.size(), write.sha256()),
                "RESERVED");
        client.upload(write.stagedPath(), key, null, write.mimeType());
        var metadata = new SysOss();
        metadata.setOssId(write.artifactId());
        metadata.setTenantId(write.actor().tenantId());
        metadata.setCreateBy(write.actor().userId());
        metadata.setFileName(key);
        metadata.setOriginalName(write.originalFilename());
        // sys_oss.file_suffix 只有 10 字符；工程隔离标记存放在 ext1 和私有命名空间中。
        metadata.setFileSuffix(switch (write.artifactType()) {
            case PCB_SOURCE -> ".zip";
            case PCB_PREVIEW_2D -> ".png";
            case PCB_BOM, PCB_DRC_REPORT -> ".json";
            case PCB_NETLIST -> ".net";
            case PCB_MODEL_OBJ -> ".obj";
            case PCB_MODEL_MTL -> ".mtl";
            case PCB_MODEL_TEXTURE -> "image/jpeg".equals(write.mimeType()) ? ".jpg" : ".png";
        });
        metadata.setService(configKey);
        metadata.setUrl("");
        metadata.setExt1("ENGINEERING_PRIVATE");
        mapper.insert(metadata);
        return new EngineeringArtifactStored(
                write.artifactId(), configKey, key, write.size(), write.sha256());
    }

    /**
     * 上层授权后读取私有对象；再检查工程记录为已确认状态且与 sys_oss 完全对应。
     *
     * @param stored 已通过本人任务权限校验的内部定位
     * @return 调用方负责关闭的文件流，不生成公共 URL
     * @throws IOException 内部归档记录不存在或对象读取失败
     * @throws IllegalStateException 非已确认产物或定位与状态不匹配
     */
    @Override
    public InputStream read(EngineeringArtifactStored stored) throws IOException {
        checkNamespace(stored);
        requireArtifact(stored, "CONFIRMED");
        var metadata = mapper.selectById(stored.ossId());
        if (metadata == null
                || !Objects.equals(metadata.getFileName(), stored.objectKey())
                || !Objects.equals(metadata.getService(), stored.service()))
            throw new IOException("工程归档记录不存在");
        return client().getObjectContent(stored.objectKey());
    }

    /**
     * 仅回收 AI 服务已经 CAS 抢占 CLEANING 状态的工程对象，禁止误删已确认产物。
     *
     * @param stored 待清理产物的精确内部定位
     * @throws IOException 对象删除失败，调用方须保留待清理记录重试
     * @throws IllegalArgumentException 对象不属于工程前缀或私桶配置
     * @throws IllegalStateException 缺少清理状态证明或 owner/任务/摘要不匹配
     */
    @Override
    public void deleteUnconfirmed(EngineeringArtifactStored stored) throws IOException {
        checkNamespace(stored);
        requireArtifact(stored, "CLEANING");
        var metadata = mapper.selectById(stored.ossId());
        if (metadata != null && !Objects.equals(metadata.getFileName(), stored.objectKey()))
            throw new IllegalArgumentException("工程对象编号不匹配");
        client().delete(stored.objectKey());
        if (metadata != null) mapper.deleteById(stored.ossId());
    }

    private void requireArtifact(EngineeringArtifactStored stored, String status) {
        var row = artifacts.selectById(stored.ossId());
        if (row == null
                || !status.equals(row.getStatus())
                || !Objects.equals(row.getOssId(), stored.ossId())
                || !Objects.equals(row.getObjectKey(), stored.objectKey())
                || !Objects.equals(row.getService(), stored.service())
                || !Objects.equals(row.getSha256(), stored.sha256())
                || row.getSize() == null
                || row.getSize() != stored.size())
            throw new IllegalStateException("工程产物状态或内部定位不匹配，禁止存储操作");
        String expected =
                EngineeringObjectNamespace.key(
                        new EngineeringActor(row.getTenantId(), row.getUserId(), Set.of()),
                        row.getTaskId(),
                        row.getArtifactId());
        if (!expected.equals(stored.objectKey())) throw new IllegalStateException("工程产物命名空间归属不匹配");
    }

    private void checkNamespace(EngineeringArtifactStored stored) {
        if (stored == null
                || !EngineeringObjectNamespace.isEngineering(stored.objectKey())
                || !Objects.equals(configKey, stored.service())
                || !stored.objectKey()
                        .matches("engineering/[A-Za-z0-9_-]{1,20}/[0-9]+/[0-9]+/[0-9]+"))
            throw new IllegalArgumentException("禁止访问工程命名空间之外的对象");
    }
}
