package com.nexus.report.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;

/** 报表定义权限边界测试，负责当前模块对应的契约或处理职责。 */
class ReportDefinitionSecurityTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /**
     * 验证 {@code draftPreviewProjectsDeclaredColumnsEvenWhenProviderReturnsPrivateFields}
     * 场景下的结果与边界条件。
     */
    @Test
    void draftPreviewProjectsDeclaredColumnsEvenWhenProviderReturnsPrivateFields() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var result =
                    f.definitions.preview(
                            f.viewer, f.definition.id(), f.dsl("TEST_REPORT"), Map.of(), null);
            assertEquals(Set.of("code"), result.rows().get(0).keySet());
            assertEquals("001", result.rows().get(0).get("code").value());
        }
    }

    /** 验证 {@code draftPreviewDoesNotReturnRowsAfterEditAclWasRevokedDuringQuery} 场景下的结果与边界条件。 */
    @Test
    void draftPreviewDoesNotReturnRowsAfterEditAclWasRevokedDuringQuery() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.provider.onQuery =
                    () ->
                            f.store.replaceAcl(
                                    f.owner,
                                    "REPORT",
                                    f.definition.id(),
                                    List.of(new Grant("USER", "8", "VIEW")));
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.preview(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    f.dsl("TEST_REPORT"),
                                                    Map.of(),
                                                    null))
                            .code());
        }
    }

    /** 验证 {@code draftPreviewDoesNotReturnRowsAfterItsDataScopeChanged} 场景下的结果与边界条件。 */
    @Test
    void draftPreviewDoesNotReturnRowsAfterItsDataScopeChanged() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.provider.onQuery = () -> f.narrow(f.viewer.userId());
            assertEquals(
                    "SCOPE_CHANGED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.preview(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    f.dsl("TEST_REPORT"),
                                                    Map.of(),
                                                    null))
                            .code());
        }
    }

    /** 验证 {@code sampleProjectsAuthorizedFieldsAndRechecksDatasetAclAfterTheQuery} 场景下的结果与边界条件。 */
    @Test
    void sampleProjectsAuthorizedFieldsAndRechecksDatasetAclAfterTheQuery() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var result = f.definitions.sample(f.viewer, f.dataset.id(), Map.of());
            assertEquals(Set.of("code"), result.rows().get(0).keySet());
            f.provider.onQuery =
                    () ->
                            f.store.replaceAcl(
                                    f.root,
                                    "DATASET",
                                    f.dataset.id(),
                                    List.of(new Grant("USER", "7", "VIEW")));
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () -> f.definitions.sample(f.viewer, f.dataset.id(), Map.of()))
                            .code());
        }
    }

    /**
     * 验证 {@code previewCannotChangeImmutableDefinitionIdentityOrUndeclaredQueryParameters}
     * 场景下的结果与边界条件。
     */
    @Test
    void previewCannotChangeImmutableDefinitionIdentityOrUndeclaredQueryParameters() {
        try (var f = new RuntimeReviewFixture(directory)) {
            assertEquals(
                    "INVALID_TEMPLATE",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.preview(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    f.dsl("OTHER_REPORT"),
                                                    Map.of(),
                                                    null))
                            .code());
            assertEquals(
                    "INVALID_PARAMETER",
                    assertThrows(
                                    DatasetAccessException.class,
                                    () ->
                                            f.definitions.preview(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    f.dsl("TEST_REPORT"),
                                                    Map.of("customerId", "9"),
                                                    null))
                            .code());
            assertEquals(0, f.provider.queries.size());
        }
    }

    /**
     * 验证 {@code fingerprintUsesCanonicalEffectiveScopeAndIgnoresUnrelatedRoleOrder} 场景下的结果与边界条件。
     */
    @Test
    void fingerprintUsesCanonicalEffectiveScopeAndIgnoresUnrelatedRoleOrder() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var binding = f.definitions.bind(f.owner, f.dsl("TEST_REPORT"));
            var all = new ReportRole(2, Set.of("wm:stock:list"), ReportScope.all(7));
            var redundant =
                    new ReportRole(
                            3,
                            Set.of("wm:stock:list", "unrelated:read"),
                            new ReportScope(false, Set.of(10L), true, 7));
            var one = new ReportActor("A", 7, 10, false, List.of(all, redundant));
            var two = new ReportActor("A", 7, 10, false, List.of(redundant, all));
            assertEquals(
                    f.definitions.fingerprint(one, binding),
                    f.definitions.fingerprint(two, binding));
            assertEquals(
                    f.definitions.fingerprint(one, binding),
                    f.definitions.fingerprint(
                            new ReportActor("A", 7, 10, false, List.of(all)), binding));
            var narrowed =
                    RuntimeReviewFixture.actor(
                            7,
                            Set.of("wm:stock:list"),
                            new ReportScope(false, Set.of(10L), false, 7));
            assertNotEquals(
                    f.definitions.fingerprint(one, binding),
                    f.definitions.fingerprint(narrowed, binding));
        }
    }

    /** 验证 {@code authorizationResolutionCannotSubstituteAnotherTenantOrUser} 场景下的结果与边界条件。 */
    @Test
    void authorizationResolutionCannotSubstituteAnotherTenantOrUser() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.resolveOverride = (tenant, user) -> f.root;
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(ReportFault.class, () -> f.definitions.resolve("A", 8)).code());
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(ReportFault.class, () -> f.definitions.resolve("B", 1)).code());
            f.resolveOverride = (tenant, user) -> null;
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(ReportFault.class, () -> f.definitions.resolve("A", 8)).code());
        }
    }

    /** 验证 {@code publishCannotUseAFutureRevisionToPublishPreviouslyLoadedContent} 场景下的结果与边界条件。 */
    @Test
    void publishCannotUseAFutureRevisionToPublishPreviouslyLoadedContent() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var current = f.store.definition("A", f.definition.id()).orElseThrow();
            var descriptors = new AtomicInteger();
            f.provider.onDescriptor =
                    () -> {
                        if (descriptors.incrementAndGet() == 1)
                            f.store.saveDraft(
                                    f.owner,
                                    current.id(),
                                    current.revision(),
                                    "并发编辑",
                                    current.json());
                    };
            assertEquals(
                    "REVISION_CONFLICT",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.publish(
                                                    f.owner, current.id(), current.revision() + 1))
                            .code());
            assertEquals(0, descriptors.get());
            assertEquals(1, f.store.versions("A", current.id()).size());
        }
    }

    /**
     * 验证 {@code oldStateOverloadStillComparesTheRevisionItReadBeforeAConcurrentEdit} 场景下的结果与边界条件。
     */
    @Test
    void oldStateOverloadStillComparesTheRevisionItReadBeforeAConcurrentEdit() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var current = f.store.definition("A", f.definition.id()).orElseThrow();
            var once = new AtomicBoolean();
            f.onDefinitionRead =
                    () -> {
                        if (once.compareAndSet(false, true))
                            f.store.saveDraft(
                                    f.owner,
                                    current.id(),
                                    current.revision(),
                                    "并发编辑",
                                    current.json());
                    };
            assertEquals(
                    "REVISION_CONFLICT",
                    assertThrows(
                                    ReportFault.class,
                                    () -> f.definitions.state(f.owner, current.id(), "OFFLINE"))
                            .code());
            assertEquals("PUBLISHED", f.store.definition("A", current.id()).orElseThrow().status());
        }
    }

    /** 验证 {@code rollbackOverloadsFenceConcurrentEditsAndExplicitStaleRevisions} 场景下的结果与边界条件。 */
    @Test
    void rollbackOverloadsFenceConcurrentEditsAndExplicitStaleRevisions() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var current = f.store.definition("A", f.definition.id()).orElseThrow();
            assertEquals(
                    "REVISION_CONFLICT",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.rollback(
                                                    f.owner,
                                                    current.id(),
                                                    current.publishedVersion(),
                                                    current.revision() - 1))
                            .code());
            var once = new AtomicBoolean();
            f.provider.onDescriptor =
                    () -> {
                        if (once.compareAndSet(false, true))
                            f.store.saveDraft(
                                    f.owner,
                                    current.id(),
                                    current.revision(),
                                    "并发编辑",
                                    current.json());
                    };
            assertEquals(
                    "REVISION_CONFLICT",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.rollback(
                                                    f.owner,
                                                    current.id(),
                                                    current.publishedVersion()))
                            .code());
        }
    }

    /**
     * 验证 {@code datasetStateRequiresItsExplicitExpectedStatusAndArchiveAclCannotBeEdited}
     * 场景下的结果与边界条件。
     */
    @Test
    void datasetStateRequiresItsExplicitExpectedStatusAndArchiveAclCannotBeEdited() {
        try (var f = new RuntimeReviewFixture(directory)) {
            f.definitions.datasetState(f.root, f.dataset.id(), "OFFLINE", "ACTIVE");
            assertEquals(
                    "REVISION_CONFLICT",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.datasetState(
                                                    f.root, f.dataset.id(), "ACTIVE", "ACTIVE"))
                            .code());
            var before = f.store.acl("A", "DATASET", f.dataset.id());
            f.store.datasetState(f.root, f.dataset.id(), "ARCHIVED");
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.acl(
                                                    f.root, "DATASET", f.dataset.id(), List.of()))
                            .code());
            assertEquals(before, f.store.acl("A", "DATASET", f.dataset.id()));
        }
    }

    /**
     * 验证 {@code aclRechecksArchiveAfterSubjectValidationAndDoesNotReplaceExistingGrants}
     * 场景下的结果与边界条件。
     */
    @Test
    void aclRechecksArchiveAfterSubjectValidationAndDoesNotReplaceExistingGrants() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var before = f.store.acl("A", "REPORT", f.definition.id());
            var once = new AtomicBoolean();
            f.onSubjectCheck =
                    () -> {
                        if (once.compareAndSet(false, true))
                            f.store.definitionState(f.owner, f.definition.id(), "ARCHIVED");
                    };
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.acl(
                                                    f.owner,
                                                    "REPORT",
                                                    f.definition.id(),
                                                    List.of(new Grant("USER", "8", "VIEW"))))
                            .code());
            assertEquals(before, f.store.acl("A", "REPORT", f.definition.id()));
        }
    }

    /** 验证 {@code publishedPointerMustBelongToTheRequestedReport} 场景下的结果与边界条件。 */
    @Test
    void publishedPointerMustBelongToTheRequestedReport() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var other = f.definitions.create(f.owner, f.dsl("OTHER_REPORT"), null);
            var version = f.definitions.publish(f.owner, other.id(), other.revision());
            f.jdbc.update(
                    "UPDATE rpt_definition SET published_version_id=? WHERE report_id=?",
                    Long.valueOf(version.id()),
                    Long.valueOf(f.definition.id()));
            assertEquals(
                    "NOT_FOUND",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.published(
                                                    f.viewer,
                                                    f.definition.id(),
                                                    "VIEW",
                                                    "report:runtime:preview"))
                            .code());
        }
    }

    /**
     * 验证 {@code bootstrapUsesCanonicalRootAndDoesNotExposeOrReviveInaccessibleExistingDefinitions}
     * 场景下的结果与边界条件。
     */
    @Test
    void bootstrapUsesCanonicalRootAndDoesNotExposeOrReviveInaccessibleExistingDefinitions() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var foreignOwner = RuntimeReviewFixture.actor(9, Set.of("*:*:*"), ReportScope.all(9));
            var hidden =
                    f.store.createDefinition(
                            foreignOwner,
                            "BASE_SECURE_TEST",
                            "隐藏模板",
                            "TABLE",
                            null,
                            f.compiler.json(f.dsl("BASE_SECURE_TEST")));
            var datasetGrants = new ArrayList<>(f.store.acl("A", "DATASET", f.dataset.id()));
            datasetGrants.add(new Grant("USER", "9", "VIEW"));
            f.store.replaceAcl(f.root, "DATASET", f.dataset.id(), datasetGrants);
            f.store.publish(
                    foreignOwner,
                    hidden.id(),
                    hidden.revision(),
                    hidden.json(),
                    "hash",
                    f.dataset.publishedVersion());
            assertTrue(
                    f.definitions.bootstrap(f.owner).stream()
                            .noneMatch(d -> d.id().equals(hidden.id())));
            f.store.definitionState(foreignOwner, hidden.id(), "ARCHIVED");
            assertTrue(
                    f.definitions.bootstrap(f.root).stream()
                            .noneMatch(d -> d.id().equals(hidden.id())));
            assertEquals("ARCHIVED", f.store.definition("A", hidden.id()).orElseThrow().status());
            assertEquals(
                    1,
                    f.store.catalogs("A").stream()
                            .filter(c -> c.parentId().equals("0") && c.name().equals("报表中心"))
                            .count());
        }
    }

    /** 验证 {@code datasetViewRevokedAfterCompileCannotPublishANewVersion} 场景下的结果与边界条件。 */
    @Test
    void datasetViewRevokedAfterCompileCannotPublishANewVersion() {
        try (var f = new RuntimeReviewFixture(directory)) {
            var current = f.definitions.detail(f.owner, f.definition.id());
            f.provider.onDescriptor =
                    () -> f.store.replaceAcl(f.root, "DATASET", f.dataset.id(), List.of());
            assertEquals(
                    "SCOPE_DENIED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            f.definitions.publish(
                                                    f.owner, current.id(), current.revision()))
                            .code());
            assertEquals(1, f.store.versions("A", current.id()).size());
        }
    }
}
