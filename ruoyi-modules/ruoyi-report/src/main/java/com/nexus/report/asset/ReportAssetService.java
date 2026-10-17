package com.nexus.report.asset;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.service.ReportDefinitionService;
import com.nexus.report.storage.ReportArtifactStore;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 私有图片的不可变版本和ACL，与公共OSS附件链接解耦。 */
public final class ReportAssetService {
    /**
     * 不可变图片契约，不包含公开地址或本机路径。
     *
     * @param store 逻辑存储标识
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     * @param bytes 保留或实际占用的字节数
     * @param width 布局宽度
     * @param height 布局高度
     * @param format 受控格式
     */
    public record Asset(
            String store,
            String reference,
            String checksum,
            long bytes,
            int width,
            int height,
            String format) {}

    /**
     * 面向编辑器的固定版本元数据，仅投影已授权的名称、标识与图片像素尺寸。
     *
     * @param versionId 请求绑定的不可变版本标识
     * @param resourceId 当前授权的资产资源标识
     * @param name 当前授权草稿的标题
     * @param width 固定版本的图片像素宽度
     * @param height 固定版本的图片像素高度
     */
    public record ReportAssetMetadata(
            String versionId, String resourceId, String name, int width, int height) {}

    /**
     * 单次授权解析的固定版本、当前草稿与私有资产，仅用于服务内部投影。
     *
     * @param version 已校验完整性的不可变版本
     * @param draft 当前身份具有VIEW权限的资源草稿
     * @param asset 固定版本中的私有资产契约
     */
    private record AuthorizedAsset(
            ReportResourceStore.Version version, ReportResourceStore.Draft draft, Asset asset) {}

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前artifacts状态或配置。 */
    private final ReportArtifactStore artifacts;

    /** 保存当前authorization状态或配置。 */
    private final ReportAuthorizationPort authorization;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /**
     * 绑定租户化元数据、私有存储和可刷新身份。
     *
     * @param resources 当前契约的resources配置或状态
     * @param artifacts 当前契约的artifacts配置或状态
     * @param authorization 当前契约的authorization配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportAssetService(
            ReportResourceStore resources,
            ReportArtifactStore artifacts,
            ReportAuthorizationPort authorization,
            ReportCompiler json) {
        this.resources = resources;
        this.artifacts = artifacts;
        this.authorization = authorization;
        this.json = json;
    }

    /**
     * 重新解析当前用户，禁止使用已禁用账户的历史身份。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    private ReportActor current(ReportActor actor) {
        var current = authorization.resolve(actor.tenantId(), actor.userId());
        if (current == null
                || !current.tenantId().equals(actor.tenantId())
                || current.userId() != actor.userId()) throw ReportFault.denied();
        return current;
    }

    /**
     * 管理目录只返回当前身份可见的私有资产定义。
     *
     * @param actor 执行用户
     * @return 当前受控处理的固定结果
     */
    public List<ReportResourceStore.Draft> list(ReportActor actor) {
        var current = current(actor);
        ReportDefinitionService.require(current, "report:asset:list");
        return resources.list("ASSET", current);
    }

    /**
     * 规范化上传后一次性创建定义及发布版本，事务失败回收未注册对象。
     *
     * @param actor 执行用户
     * @param code 当前契约的code配置或状态
     * @param name 契约名称
     * @param bytes 保留或实际占用的字节数
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Version upload(
            ReportActor actor, String code, String name, byte[] bytes) {
        var current = current(actor);
        ReportDefinitionService.require(current, "report:asset:manage");
        var image = new ImageNormalizer().normalize(bytes);
        Path staging = artifacts.temporary(current.tenantId(), "asset");
        String reference = null;
        boolean registered = false;
        try {
            try (var output = artifacts.output(current.tenantId(), staging)) {
                output.write(image.bytes());
            }
            reference = artifacts.commit(current.tenantId(), staging, "asset");
            var asset =
                    new Asset(
                            artifacts.storeId(),
                            reference,
                            artifacts.checksum(current.tenantId(), reference),
                            image.bytes().length,
                            image.width(),
                            image.height(),
                            image.format());
            var fixed = current(current);
            ReportDefinitionService.require(fixed, "report:asset:manage");
            var result =
                    resources.atomic(
                            () -> {
                                var draft =
                                        resources.create(
                                                "ASSET", fixed, code, name, json.json(asset));
                                var version =
                                        resources.publish(
                                                "ASSET", fixed, draft.id(), draft.revision(), "{}");
                                resources.artifact("ASSET", fixed, version.id(), asset.reference());
                                return version;
                            });
            registered = true;
            return result;
        } catch (IOException failure) {
            throw new ReportFault("ASSET_UPLOAD_FAILED", "私有图片保存失败", 503);
        } finally {
            artifacts.discard(current.tenantId(), staging);
            if (!registered && reference != null) artifacts.delete(current.tenantId(), reference);
        }
    }

    /**
     * 每次绑定和交付都验证资产资源ACL及固定版本完整性。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @return 当前受控处理的固定结果
     */
    public Asset resolve(ReportActor actor, String versionId) {
        return resolveCurrent(current(actor), versionId).asset();
    }

    /**
     * 重新验证当前预览权限并返回安全元数据，名称与尺寸分别取授权草稿和固定版本。
     *
     * @param actor 请求携带的身份快照
     * @param versionId 请求绑定的不可变版本标识
     * @return 不含内部存储字段的元数据
     */
    public ReportAssetMetadata metadata(ReportActor actor, String versionId) {
        var current = current(actor);
        ReportDefinitionService.require(current, "report:runtime:preview");
        var resolved = resolveCurrent(current, versionId);
        return new ReportAssetMetadata(
                resolved.version().id(),
                resolved.version().resourceId(),
                resolved.draft().title(),
                resolved.asset().width(),
                resolved.asset().height());
    }

    /**
     * 在已刷新的身份下复用租户、VIEW、下线、版本完整性与存储挂载检查。
     *
     * @param current 重新解析的当前身份
     * @param versionId 请求绑定的不可变版本标识
     * @return 同一次授权解析的固定版本和当前草稿
     */
    private AuthorizedAsset resolveCurrent(ReportActor current, String versionId) {
        var version =
                resources
                        .version("ASSET", current.tenantId(), versionId)
                        .orElseThrow(() -> new ReportFault("ASSET_NOT_FOUND", "私有图片版本不存在", 404));
        var draft = resources.access("ASSET", current, version.resourceId(), "VIEW");
        if (!draft.status().equals("PUBLISHED"))
            throw new ReportFault("ASSET_OFFLINE", "私有图片已下线", 409);
        var asset = json.read(version.json(), Asset.class);
        if (!asset.store().equals(artifacts.storeId()))
            throw new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "图片存储空间未挂载", 503);
        return new AuthorizedAsset(version, draft, asset);
    }

    /**
     * 输出规范化PNG前后重新鉴权，并校验存储checksum。
     *
     * @param actor 执行用户
     * @param versionId 不可变发布版本标识
     * @return 当前受控处理的固定结果
     */
    public byte[] bytes(ReportActor actor, String versionId) {
        var asset = resolve(actor, versionId);
        if (!asset.checksum().equals(artifacts.checksum(actor.tenantId(), asset.reference())))
            throw new ReportFault("ASSET_INVALID", "图片完整性校验失败", 409);
        try (var materialized = artifacts.materialize(actor.tenantId(), asset.reference())) {
            var path = materialized.path();
            if (Files.size(path) != asset.bytes() || asset.bytes() > 5 * 1024 * 1024)
                throw ReportFault.capacity();
            byte[] result = Files.readAllBytes(path);
            resolve(actor, versionId);
            return result;
        } catch (IOException failure) {
            throw new ReportFault("ASSET_READ_FAILED", "私有图片不可读取", 503);
        }
    }

    /**
     * 管理人员显式下线或恢复资源，不删除历史版本。
     *
     * @param actor 执行用户
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     * @return 当前受控处理的固定结果
     */
    public ReportResourceStore.Draft state(
            ReportActor actor, String id, long revision, String state) {
        var current = current(actor);
        ReportDefinitionService.require(current, "report:asset:manage");
        return resources.state("ASSET", current, id, revision, state);
    }
}
