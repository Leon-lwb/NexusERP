package com.nexus.report.asset;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.*;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

/** 私有资产真实存储、版本完整性、租户和下线回归。 */
class ReportAssetTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

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
