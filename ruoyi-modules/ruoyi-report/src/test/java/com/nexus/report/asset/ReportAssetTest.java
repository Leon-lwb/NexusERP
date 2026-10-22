package com.nexus.report.asset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.nexus.common.report.*;
import com.nexus.report.controller.ReportAssetController;
import com.nexus.report.controller.ReportExceptionHandler;
import com.nexus.report.controller.ReportRequestActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords;
import com.nexus.report.extension.*;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** 私有资产真实存储、版本完整性、租户和下线回归。 */
class ReportAssetTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 元数据名称取当前授权草稿，旧版本尺寸保持固定，JSON只暴露五个公开字段。 */
    @Test
    void metadataProjectsCurrentTitleAndFixedVersionDimensionsWithoutStorageFields()
            throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "OLD_LOGO", "原始标题", image(40, 20));
            var replacement =
                    service.upload(fixture.owner, "NEW_LOGO", "替换图片", image(80, 60));
            var draft = resources.read("ASSET", "A", version.resourceId()).orElseThrow();
            draft =
                    resources.save(
                            "ASSET",
                            fixture.owner,
                            draft.id(),
                            draft.revision(),
                            replacement.json(),
                            "当前授权标题");
            var latest =
                    resources.publish(
                            "ASSET", fixture.owner, draft.id(), draft.revision(), "{}");
            var response = metadata(service, fixture.owner, version.id());
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("no-store, private", response.getHeader("Cache-Control"));
            var data = fixture.json.readTree(response.getContentAsString()).get("data");
            var fields = new HashSet<String>();
            data.fieldNames().forEachRemaining(fields::add);
            assertEquals(Set.of("versionId", "resourceId", "name", "width", "height"), fields);
            assertEquals(version.id(), data.get("versionId").asText());
            assertTrue(data.get("versionId").isTextual());
            assertEquals(version.resourceId(), data.get("resourceId").asText());
            assertTrue(data.get("resourceId").isTextual());
            assertEquals("当前授权标题", data.get("name").asText());
            assertEquals(40, data.get("width").asInt());
            assertEquals(20, data.get("height").asInt());
            var asset = service.resolve(fixture.owner, version.id());
            assertFalse(response.getContentAsString().contains(asset.reference()));
            assertFalse(response.getContentAsString().contains(asset.checksum()));
            var latestResponse = metadata(service, fixture.owner, latest.id());
            assertEquals(200, latestResponse.getStatus(), latestResponse.getContentAsString());
            var latestData =
                    fixture.json.readTree(latestResponse.getContentAsString()).get("data");
            assertEquals(80, latestData.get("width").asInt());
            assertEquals(60, latestData.get("height").asInt());
        }
    }

    /** VIEW授权及撤销即时生效，不因曾成功加载图片而继续暴露元数据。 */
    @Test
    void metadataRequiresCurrentViewAclAndRejectsRevocation() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "ACL_LOGO", "授权图片", image(40, 20));
            assertMetadataFailure(
                    fixture, metadata(service, fixture.viewer, version.id()), 403, "SCOPE_DENIED");
            fixture.store.replaceAcl(
                    fixture.owner,
                    "ASSET",
                    version.resourceId(),
                    List.of(new ReportRecords.Grant("USER", "8", "VIEW")));
            var granted = metadata(service, fixture.viewer, version.id());
            assertEquals(200, granted.getStatus(), granted.getContentAsString());
            fixture.store.replaceAcl(fixture.owner, "ASSET", version.resourceId(), List.of());
            assertMetadataFailure(
                    fixture, metadata(service, fixture.viewer, version.id()), 403, "SCOPE_DENIED");
        }
    }

    /** 当前身份失去preview权限时，即使仍拥有资产也禁止读取元数据。 */
    @Test
    void metadataChecksRefreshedPreviewPermissionInsideTheService() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "PREVIEW_LOGO", "预览图片", image(40, 20));
            fixture.actors.put(
                    fixture.owner.userId(),
                    RuntimeReviewFixture.actor(
                            7, Set.of("report:asset:list"), ReportScope.all(7)));
            assertMetadataFailure(
                    fixture, metadata(service, fixture.owner, version.id()), 403, "SCOPE_DENIED");
        }
    }

    /** 已禁用账户的旧身份不能继续访问此前绑定的图片版本元数据。 */
    @Test
    void metadataRejectsDisabledAccountsInsteadOfTrustingTheRequestActor() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "ACCOUNT_LOGO", "账户图片", image(40, 20));
            fixture.actors.remove(fixture.owner.userId());
            assertMetadataFailure(
                    fixture, metadata(service, fixture.owner, version.id()), 403, "SCOPE_DENIED");
        }
    }

    /** 其他租户的有效管理员也不能获取图片固定版本的存在性与属性。 */
    @Test
    void metadataCannotReadAnotherTenantsVersion() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "TENANT_LOGO", "租户图片", image(40, 20));
            var other = new ReportActor("B", 9, 10, true, List.of());
            fixture.actors.put(other.userId(), other);
            assertMetadataFailure(
                    fixture, metadata(service, other, version.id()), 404, "ASSET_NOT_FOUND");
        }
    }

    /** 资源下线后旧固定版本不可读取，恢复发布后仍返回原版本属性。 */
    @Test
    void metadataHonorsOfflineAndRestoredResourceState() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "STATE_LOGO", "状态图片", image(40, 20));
            var draft = resources.read("ASSET", "A", version.resourceId()).orElseThrow();
            draft = service.state(fixture.owner, draft.id(), draft.revision(), "OFFLINE");
            assertMetadataFailure(
                    fixture, metadata(service, fixture.owner, version.id()), 409, "ASSET_OFFLINE");
            service.state(fixture.owner, draft.id(), draft.revision(), "PUBLISHED");
            var response = metadata(service, fixture.owner, version.id());
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals(
                    version.id(),
                    fixture.json.readTree(response.getContentAsString())
                            .get("data")
                            .get("versionId")
                            .asText());
        }
    }

    /** 固定版本正文损坏时元数据接口沿用资源完整性校验，不能读取未验证尺寸。 */
    @Test
    void metadataRejectsTamperedImmutableVersion() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var resources = resources(fixture);
            var service = service(fixture, resources);
            var version = service.upload(fixture.owner, "FIXED_LOGO", "固定图片", image(40, 20));
            fixture.jdbc.update(
                    "UPDATE rpt_asset_version SET definition_json='{}' WHERE tenant_id=? AND version_id=?",
                    "A",
                    Long.parseLong(version.id()));
            assertMetadataFailure(
                    fixture, metadata(service, fixture.owner, version.id()), 409, "VERSION_INVALID");
        }
    }

    /**
     * 为当前隔离夹具安装扩展资源表，使用已有内存测试数据源。
     *
     * @param fixture 隔离运行夹具
     * @return 带真实租户和ACL检查的资源存储
     */
    private ReportResourceStore resources(RuntimeReviewFixture fixture) {
        ReportExtensionSchema.apply(fixture.jdbc);
        return new ReportResourceStore(
                fixture.jdbc,
                new DataSourceTransactionManager(fixture.jdbc.getDataSource()),
                fixture.store);
    }

    /**
     * 使用夹具中的最新账户列表构造资产服务，保留真实资源授权逻辑。
     *
     * @param fixture 隔离运行夹具
     * @param resources 已安装的资源存储
     * @return 当前测试使用的资产服务
     */
    private ReportAssetService service(
            RuntimeReviewFixture fixture, ReportResourceStore resources) {
        var authorization =
                new ReportAuthorizationPort() {
                    /** 根据当前租户与账户重新解析身份。 */
                    @Override
                    public ReportActor resolve(String tenant, long user) {
                        var actor = fixture.actors.get(user);
                        return actor != null && actor.tenantId().equals(tenant) ? actor : null;
                    }

                    /** 测试中授权主体由隔离夹具预置。 */
                    @Override
                    public boolean subjectExists(String tenant, String type, long user) {
                        return true;
                    }
                };
        return new ReportAssetService(resources, fixture.artifacts, authorization, fixture.compiler);
    }

    /**
     * 生成真实PNG输入，尺寸以像素表示。
     *
     * @param width 图片像素宽度
     * @param height 图片像素高度
     * @return 真实PNG内容
     */
    private byte[] image(int width, int height) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    /**
     * 通过真实控制器查询固定版本元数据，覆盖路由及JSON序列化。
     *
     * @param service 当前资产服务
     * @param actor 请求携带的原身份
     * @param versionId 请求绑定的不可变版本
     * @return 实际HTTP响应
     */
    private MockHttpServletResponse metadata(
            ReportAssetService service, ReportActor actor, String versionId) throws Exception {
        var actors = mock(ReportRequestActor.class);
        when(actors.current()).thenReturn(actor);
        return MockMvcBuilders.standaloneSetup(new ReportAssetController(actors, service))
                .setControllerAdvice(new ReportExceptionHandler())
                .build()
                .perform(get("/report/assets/versions/{id}/metadata", versionId))
                .andReturn()
                .getResponse();
    }

    /**
     * 核对拒绝请求的HTTP状态和既有业务错误信封。
     *
     * @param fixture 隔离运行夹具
     * @param response 实际HTTP响应
     * @param status 预期HTTP状态
     * @param code 预期业务错误编码
     */
    private void assertMetadataFailure(
            RuntimeReviewFixture fixture,
            MockHttpServletResponse response,
            int status,
            String code)
            throws Exception {
        assertEquals(status, response.getStatus(), response.getContentAsString());
        var body = fixture.json.readTree(response.getContentAsString());
        assertTrue(body.has("code"), response.getContentAsString());
        assertTrue(body.has("errorCode"), response.getContentAsString());
        assertEquals(status, body.get("code").asInt());
        assertEquals(code, body.get("errorCode").asText());
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
    }

    /** 执行privateImageVersionRejectsOtherUserTenantTamperingAndOffline对应的受控处理。 */
    @Test
    void privateImageVersionRejectsOtherUserTenantTamperingAndOffline() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(fixture.jdbc);
            var resources =
                    new ReportResourceStore(
                            fixture.jdbc,
                            new DataSourceTransactionManager(fixture.jdbc.getDataSource()),
                            fixture.store);
            var auth =
                    new ReportAuthorizationPort() {
                        /**
                         * 执行resolve对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param user 当前受控操作的user输入
                         * @return 当前固定契约或处理结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            var actor = fixture.actors.get(user);
                            return actor != null && actor.tenantId().equals(tenant) ? actor : null;
                        }

                        /**
                         * 执行subjectExists对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param type 当前受控操作的type输入
                         * @param user 当前受控操作的user输入
                         * @return 当前固定契约或处理结果
                         */
                        public boolean subjectExists(String tenant, String type, long user) {
                            return true;
                        }
                    };
            var service =
                    new ReportAssetService(resources, fixture.artifacts, auth, fixture.compiler);
            var image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            var version =
                    service.upload(fixture.owner, "PRIVATE_LOGO", "私有标志", bytes.toByteArray());
            assertEquals(40, service.resolve(fixture.owner, version.id()).width());
            assertNotEquals(0, service.bytes(fixture.owner, version.id()).length);
            assertThrows(ReportFault.class, () -> service.bytes(fixture.viewer, version.id()));
            assertThrows(
                    ReportFault.class,
                    () ->
                            service.bytes(
                                    new ReportActor("B", 7, 10, true, List.of()), version.id()));
            var draft = resources.read("ASSET", "A", version.resourceId()).orElseThrow();
            service.state(fixture.owner, draft.id(), draft.revision(), "OFFLINE");
            assertThrows(ReportFault.class, () -> service.bytes(fixture.owner, version.id()));
            draft = resources.read("ASSET", "A", draft.id()).orElseThrow();
            service.state(fixture.owner, draft.id(), draft.revision(), "PUBLISHED");
            var fixed = service.resolve(fixture.owner, version.id());
            Files.write(fixture.artifacts.read("A", fixed.reference()), new byte[] {1, 2, 3});
            assertThrows(ReportFault.class, () -> service.bytes(fixture.owner, version.id()));
        }
    }
}
