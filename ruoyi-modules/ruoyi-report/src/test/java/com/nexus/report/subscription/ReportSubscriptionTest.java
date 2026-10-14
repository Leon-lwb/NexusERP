package com.nexus.report.subscription;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.*;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.*;
import com.nexus.report.export.ReportExportRenderer;
import com.nexus.report.extension.*;
import com.nexus.report.runtime.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 订阅分别使用接收人身份，调度、导出和站内领取都保持幂等与重新授权。 */
class ReportSubscriptionTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行scheduledRecipientsHaveDifferentPrivateRunsAndOfflineBlocksDirectExportDownload对应的受控处理。 */
    @Test
    void scheduledRecipientsHaveDifferentPrivateRunsAndOfflineBlocksDirectExportDownload()
            throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            f.store.admissionLimits(50, 100, 1000);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var props = new ReportSubscriptionProperties();
            props.setSchedulingEnabled(true);
            props.setNotificationsEnabled(true);
            var notices = new ArrayList<String>();
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
                            return f.actors.get(user);
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
                            return f.actors.containsKey(user);
                        }
                    };
            var peer =
                    RuntimeReviewFixture.actor(
                            9, Set.of("*:*:*"), new ReportScope(false, Set.of(10L), false, 9));
            f.actors.put(9L, peer);
            f.store.replaceAcl(
                    f.owner,
                    "REPORT",
                    f.definition.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            f.store.replaceAcl(
                    f.root,
                    "DATASET",
                    f.dataset.id(),
                    List.of(
                            new Grant("USER", "7", "VIEW"),
                            new Grant("USER", "7", "EXPORT"),
                            new Grant("USER", "9", "VIEW"),
                            new Grant("USER", "9", "EXPORT")));
            var subscriptions =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props);
            var deliveries =
                    new ReportDeliveryService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            (tenant, user, id, title) -> notices.add(id));
            f.runtime.constraints(
                    new ReportSubscriptionAccessGuard(
                                    resources, f.jdbc, auth, f.compiler, f.definitions)
                            ::check);
            var version =
                    f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion();
            var model =
                    new SubscriptionDefinition(
                            1,
                            version,
                            "XLSX_DATA",
                            Map.of(),
                            List.of("7", "9"),
                            "0 0 * * * *",
                            "Asia/Shanghai",
                            "EARLIER_OFFSET",
                            3600,
                            true);
            var draft = subscriptions.create(f.owner, "SUBSCRIPTION_TEST", "合成订阅", model);
            subscriptions.publish(f.owner, draft.id(), draft.revision());
            Instant scheduled = Instant.parse("2026-10-15T00:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?",
                    java.sql.Timestamp.from(scheduled));
            subscriptions.dispatchDue(scheduled);
            subscriptions.dispatchDue(scheduled);
            assertEquals(
                    2,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_subscription_run", Integer.class));
            assertEquals(
                    2, f.jdbc.queryForObject("SELECT COUNT(*) FROM rpt_delivery", Integer.class));
            var ownerJob = f.runtime.jobs(f.owner).get(0);
            var peerJob = f.runtime.jobs(peer).get(0);
            assertNotEquals(ownerJob.runId(), peerJob.runId());
            assertNotEquals(
                    f.store.run("A", ownerJob.runId()).orElseThrow().scopeHash(),
                    f.store.run("A", peerJob.runId()).orElseThrow().scopeHash());
            complete(f, ownerJob);
            complete(f, peerJob);
            deliveries.reconcile();
            deliveries.reconcile();
            assertEquals(2, notices.size());
            var ownerDelivery = deliveries.list(f.owner).get(0);
            assertEquals(ownerJob.id(), deliveries.claim(f.owner, ownerDelivery.id()));
            f.actors.put(
                    7L,
                    RuntimeReviewFixture.actor(
                            7,
                            Set.of(
                                    "report:subscription:publish",
                                    "report:subscription:manage",
                                    "report:runtime:preview",
                                    "report:runtime:export"),
                            ReportScope.all(7)));
            assertThrows(ReportFault.class, () -> f.runtime.job(peer, peerJob.id()));
            f.actors.put(7L, f.owner);
            assertThrows(ReportFault.class, () -> deliveries.claim(peer, ownerDelivery.id()));
            var current = resources.read("SUBSCRIPTION", "A", draft.id()).orElseThrow();
            subscriptions.state(f.owner, draft.id(), current.revision(), "OFFLINE");
            assertThrows(ReportFault.class, () -> deliveries.claim(f.owner, ownerDelivery.id()));
            assertThrows(ReportFault.class, () -> f.runtime.job(f.owner, ownerJob.id()));
        }
    }

    /** 一个接收人队列满也不能回滚另一人的执行，断点保持可重试记录。 */
    @Test
    void fullRecipientQueueDoesNotRollbackPeerOccurrence() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var properties = new ReportSubscriptionProperties();
            properties.setSchedulingEnabled(true);
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
                            return f.actors.get(user);
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
            f.runtime.constraints(
                    new ReportSubscriptionAccessGuard(
                                    resources, f.jdbc, auth, f.compiler, f.definitions)
                            ::check);
            var peer = RuntimeReviewFixture.actor(9, Set.of("*:*:*"), ReportScope.all(9));
            f.actors.put(9L, peer);
            f.store.replaceAcl(
                    f.owner,
                    "REPORT",
                    f.definition.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            f.store.replaceAcl(
                    f.root,
                    "DATASET",
                    f.dataset.id(),
                    List.of(
                            new Grant("USER", "7", "VIEW"),
                            new Grant("USER", "7", "EXPORT"),
                            new Grant("USER", "9", "VIEW"),
                            new Grant("USER", "9", "EXPORT")));
            var run = f.runtime.create(f.owner, f.definition.id(), "SNAPSHOT", Map.of());
            f.runtime.enqueue(f.owner, run.id(), "XLSX_DATA", "occupy-owner");
            var service =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            properties);
            var version =
                    f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion();
            var model =
                    new SubscriptionDefinition(
                            1,
                            version,
                            "XLSX_DATA",
                            Map.of(),
                            List.of("7", "9"),
                            "0 0 * * * *",
                            "UTC",
                            "EARLIER_OFFSET",
                            3600,
                            false);
            var draft = service.create(f.owner, "QUEUE_SUB", "队列隔离", model);
            service.publish(f.owner, draft.id(), draft.revision());
            Instant now = Instant.parse("2026-10-15T00:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", java.sql.Timestamp.from(now));
            assertDoesNotThrow(() -> service.dispatchDue(now));
            assertEquals(
                    2,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_subscription_run", Integer.class));
            assertEquals(1, f.runtime.jobs(peer).size());
            assertEquals(
                    "RETRY_WAIT",
                    f.jdbc.queryForObject(
                            "SELECT state FROM rpt_subscription_run WHERE recipient_id=7",
                            String.class));
        }
    }

    /** 手动请求重放固定原定义和接收人集合，不能借重新发布新增交付。 */
    @Test
    void manualRequestReplayAfterRepublishNeverAddsAnotherRecipient() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            f.store.admissionLimits(50, 100, 1000);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var properties = new ReportSubscriptionProperties();
            var auth =
                    new ReportAuthorizationPort() {
                        /**
                         * 从隔离身份目录解析当前租户的合成用户。
                         *
                         * @param tenant 租户隔离标识
                         * @param user 当前契约的user配置或状态
                         * @return 当前受控操作的固定结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            return f.actors.get(user);
                        }

                        /**
                         * 验证隔离夹具中合成授权主体是否存在。
                         *
                         * @param tenant 租户隔离标识
                         * @param type 受控类型
                         * @param user 当前契约的user配置或状态
                         * @return 当前受控操作的固定结果
                         */
                        public boolean subjectExists(String tenant, String type, long user) {
                            return true;
                        }
                    };
            f.runtime.constraints(
                    new ReportSubscriptionAccessGuard(
                                    resources, f.jdbc, auth, f.compiler, f.definitions)
                            ::check);
            var peer = RuntimeReviewFixture.actor(9, Set.of("*:*:*"), ReportScope.all(9));
            f.actors.put(9L, peer);
            f.store.replaceAcl(
                    f.owner,
                    "REPORT",
                    f.definition.id(),
                    List.of(new Grant("USER", "9", "VIEW"), new Grant("USER", "9", "EXPORT")));
            f.store.replaceAcl(
                    f.root,
                    "DATASET",
                    f.dataset.id(),
                    List.of(
                            new Grant("USER", "7", "VIEW"),
                            new Grant("USER", "7", "EXPORT"),
                            new Grant("USER", "9", "VIEW"),
                            new Grant("USER", "9", "EXPORT")));
            var service =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            properties);
            var report =
                    f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion();
            var first =
                    new SubscriptionDefinition(
                            1,
                            report,
                            "XLSX_DATA",
                            Map.of(),
                            List.of("7"),
                            "0 0 9 * * *",
                            "UTC",
                            "EARLIER_OFFSET",
                            0,
                            false);
            var draft = service.create(f.owner, "MANUAL_FIXED", "手动固定", first);
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "same-request");
            var current = resources.read("SUBSCRIPTION", "A", draft.id()).orElseThrow();
            var second =
                    new SubscriptionDefinition(
                            1,
                            report,
                            "XLSX_DATA",
                            Map.of(),
                            List.of("7", "9"),
                            first.cron(),
                            first.timezone(),
                            first.dstPolicy(),
                            0,
                            false);
            service.save(f.owner, draft.id(), current.revision(), draft.title(), second);
            current = resources.read("SUBSCRIPTION", "A", draft.id()).orElseThrow();
            service.publish(f.owner, draft.id(), current.revision());
            service.manual(f.owner, draft.id(), "same-request");
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_subscription_run", Integer.class));
            assertTrue(f.runtime.jobs(peer).isEmpty());
        }
    }

    /** 关闭补跑仍容忍正常一分钟触发器的微小调度抖动。 */
    @Test
    void zeroCatchupWindowStillFiresNormalDueOccurrence() {
        try (var f = new RuntimeReviewFixture(directory)) {
            ReportExtensionSchema.apply(f.jdbc);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var properties = new ReportSubscriptionProperties();
            properties.setSchedulingEnabled(true);
            var auth =
                    new ReportAuthorizationPort() {
                        /**
                         * 从隔离身份目录解析当前租户的合成用户。
                         *
                         * @param tenant 租户隔离标识
                         * @param user 当前契约的user配置或状态
                         * @return 当前受控操作的固定结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            return f.actors.get(user);
                        }

                        /**
                         * 验证隔离夹具中合成授权主体是否存在。
                         *
                         * @param tenant 租户隔离标识
                         * @param type 受控类型
                         * @param user 当前契约的user配置或状态
                         * @return 当前受控操作的固定结果
                         */
                        public boolean subjectExists(String tenant, String type, long user) {
                            return true;
                        }
                    };
            f.runtime.constraints(
                    new ReportSubscriptionAccessGuard(
                                    resources, f.jdbc, auth, f.compiler, f.definitions)
                            ::check);
            var service =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            properties);
            var report =
                    f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion();
            var model =
                    new SubscriptionDefinition(
                            1,
                            report,
                            "XLSX_DATA",
                            Map.of(),
                            List.of("7"),
                            "0 0 * * * *",
                            "UTC",
                            "EARLIER_OFFSET",
                            0,
                            false);
            var draft = service.create(f.owner, "ZERO_CATCHUP", "零补跑", model);
            service.publish(f.owner, draft.id(), draft.revision());
            Instant due = Instant.parse("2026-10-15T00:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", java.sql.Timestamp.from(due));
            service.dispatchDue(due.plusMillis(10));
            assertEquals(
                    1,
                    f.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM rpt_subscription_run", Integer.class));
        }
    }

    /** 使用实际快照和XLSX渲染完成持久任务，避免把假文件作为成功交付。 */

    /** 订阅未声明的provider日期默认值也必须按计划时点冻结，而非执行当天。 */
    @Test
    void omittedProviderDateDefaultsAreFrozenAcrossDelayedExecution() throws Exception {
        try (var f =
                new RuntimeReviewFixture(
                        directory.resolve("dateDefaults"),
                        List.of(
                                new DatasetParameter(
                                        "fromDate", "起日", ValueType.DATE, false, "MONTHS_AGO_6"),
                                new DatasetParameter(
                                        "toDate", "止日", ValueType.DATE, false, "TODAY")))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            props.setSchedulingEnabled(true);
            var service = subscriptions(f, resources, props);
            var model = subscription(f, List.of("7"));
            var draft = service.create(f.owner, "DEFAULT_DATE", "冻结隐含日期", model);
            service.publish(f.owner, draft.id(), draft.revision());
            Instant due = Instant.parse("2025-02-05T01:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", java.sql.Timestamp.from(due));
            service.dispatchDue(due.plusMillis(10));
            var job = f.runtime.jobs(f.owner).get(0);
            var payload = f.runtime.payload(f.store.run("A", job.runId()).orElseThrow());
            assertEquals("2025-02-05", payload.input().get("toDate"));
            assertEquals("2024-08-05", payload.input().get("fromDate"));
            complete(f, job);
            assertEquals("SUCCEEDED", f.runtime.job(f.owner, job.id()).state());
        }
    }

    /** 停止产生新cron事件时，已有授权outbox仍应在独立维护中完成。 */
    @Test
    void disabledSchedulingStillMaintainsAuthorizedPendingDelivery() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("disabled"))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            var service = subscriptions(f, resources, props);
            f.store.admissionLimits(1, 100, 1000);
            var blocker =
                    f.runtime.enqueueFixed(
                            f.owner,
                            subscription(f, List.of("7")).reportVersionId(),
                            "XLSX_DATA",
                            Map.of(),
                            "blocking");
            var draft =
                    service.create(
                            f.owner, "MANUAL_PENDING", "已有手动任务", subscription(f, List.of("7")));
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "pending");
            assertEquals("RETRY_WAIT", service.executions(f.owner, draft.id()).get(0).state());
            f.store.cancel(f.owner, blocker.id());
            f.jdbc.update(
                    "UPDATE rpt_subscription_run SET next_retry_at=?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
            var deliveries =
                    new ReportDeliveryService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            null);
            var maintenance = new ReportSubscriptionMaintenance(f.jdbc, service, deliveries);
            maintenance.tick();
            var execution = service.executions(f.owner, draft.id()).get(0);
            assertEquals("PREPARING", execution.state());
            complete(f, f.runtime.job(f.owner, execution.job()));
            maintenance.tick();
            assertEquals("READY", deliveries.list(f.owner).get(0).state());
            assertEquals(0, service.dispatchDue(Instant.now().plusSeconds(86400)));
        }
    }

    /** 满批权限失败记录不会长期阻挡后续合法交付的完成对账。 */
    @Test
    void fullUnauthorizedDeliveryBatchDoesNotStarveLaterSuccess() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("deliveryCursor"))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            var service = subscriptions(f, resources, props);
            var draft =
                    service.create(
                            f.owner, "DELIVERY_CURSOR", "交付游标", subscription(f, List.of("7")));
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "success");
            var real = f.jdbc.queryForMap("SELECT * FROM rpt_delivery");
            for (int i = 1; i <= 200; i++)
                f.jdbc.update(
                        "INSERT INTO"
                            + " rpt_delivery(delivery_id,tenant_id,execution_id,subscription_id,definition_version_id,recipient_id,job_id,notification_state,expires_at)"
                            + " VALUES(?, 'A', ?, ?, ?, 999, ?, 'DISABLED', ?)",
                        i,
                        i,
                        real.get("subscription_id"),
                        real.get("definition_version_id"),
                        real.get("job_id"),
                        real.get("expires_at"));
            complete(f, f.runtime.jobs(f.owner).get(0));
            var deliveries =
                    new ReportDeliveryService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            null);
            deliveries.reconcile();
            assertEquals(
                    "PREPARING",
                    f.jdbc.queryForObject(
                            "SELECT state FROM rpt_delivery WHERE delivery_id=?",
                            String.class,
                            real.get("delivery_id")));
            deliveries.reconcile();
            assertEquals(
                    "READY",
                    f.jdbc.queryForObject(
                            "SELECT state FROM rpt_delivery WHERE delivery_id=?",
                            String.class,
                            real.get("delivery_id")));
        }
    }

    /** 取消与入队准备争用同一execution锁，入队完成后取消必须覆盖对应作业。 */
    @Test
    void cancellationWaitsForPreparingExecutionAndCancelsItsCommittedJob() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("cancelRace"))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            var service = subscriptions(f, resources, props);
            f.store.admissionLimits(1, 100, 1000);
            var blocker =
                    f.runtime.enqueueFixed(
                            f.owner,
                            subscription(f, List.of("7")).reportVersionId(),
                            "XLSX_DATA",
                            Map.of(),
                            "block_cancel");
            var draft =
                    service.create(f.owner, "CANCEL_RACE", "取消并发", subscription(f, List.of("7")));
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "race");
            String executionId = service.executions(f.owner, draft.id()).get(0).id();
            f.store.cancel(f.owner, blocker.id());
            f.jdbc.update(
                    "UPDATE rpt_subscription_run SET next_retry_at=?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
            var entered = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            f.onDefinitionRead =
                    () -> {
                        entered.countDown();
                        try {
                            if (!resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("测试准备等待超时");
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    };
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var prepare = executor.submit(service::drain);
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                f.onDefinitionRead = () -> {};
                var cancel = executor.submit(() -> service.cancel(f.owner, executionId));
                assertThrows(
                        java.util.concurrent.TimeoutException.class,
                        () -> cancel.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
                resume.countDown();
                prepare.get(5, java.util.concurrent.TimeUnit.SECONDS);
                cancel.get(5, java.util.concurrent.TimeUnit.SECONDS);
                var execution = service.executions(f.owner, draft.id()).get(0);
                assertEquals("CANCELLED", execution.state());
                assertEquals("CANCELLED", f.store.job("A", execution.job()).orElseThrow().state());
            } finally {
                resume.countDown();
                executor.shutdownNow();
            }
        }
    }

    /** 相对开始日期与隐式结束默认值必须先按同一时区冻结，再验证区间。 */
    @Test
    void relativeDateAndImplicitDefaultAreFrozenBeforeRangeValidation() {
        try (var f =
                new RuntimeReviewFixture(
                        directory.resolve("relativeDate"),
                        List.of(
                                new DatasetParameter(
                                        "fromDate", "起日", ValueType.DATE, false, "TODAY"),
                                new DatasetParameter(
                                        "toDate", "止日", ValueType.DATE, false, "TODAY")))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            props.setSchedulingEnabled(true);
            var service = subscriptions(f, resources, props);
            var base = f.dsl("RELATIVE_DATE_REPORT");
            var dsl =
                    new ReportDsl(
                            1,
                            base.code(),
                            base.title(),
                            base.kind(),
                            base.datasetCode(),
                            base.datasetVersionId(),
                            base.columns(),
                            List.of(
                                    new ReportDsl.Parameter(
                                            "fromDate", "起日", "DATE", false, "TODAY")),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            true);
            var report = f.definitions.create(f.owner, dsl, null);
            var version = f.definitions.publish(f.owner, report.id(), report.revision());
            var model =
                    new SubscriptionDefinition(
                            1,
                            version.id(),
                            "XLSX_DATA",
                            Map.of(
                                    "fromDate",
                                    new SubscriptionDefinition.ParameterRule(
                                            "DATE_RELATIVE", "TODAY")),
                            List.of("7"),
                            "0 0 * * * *",
                            "Asia/Shanghai",
                            "EARLIER_OFFSET",
                            3600,
                            false);
            var draft = service.create(f.owner, "RELATIVE_DATE_SUB", "相对日期订阅", model);
            service.publish(f.owner, draft.id(), draft.revision());
            Instant due = Instant.parse("2027-10-15T16:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", java.sql.Timestamp.from(due));
            service.dispatchDue(due.plusMillis(1));
            assertTrue(service.schedule(f.owner, draft.id()).enabled());
            assertEquals("QUEUED", service.executions(f.owner, draft.id()).get(0).state());
            var job = f.runtime.jobs(f.owner).get(0);
            var input = f.runtime.payload(f.store.run("A", job.runId()).orElseThrow()).input();
            assertEquals("2027-10-16", input.get("fromDate"));
            assertEquals(input.get("fromDate"), input.get("toDate"));
        }
    }

    /** 准备失败回滚与补偿更新之间的取消不能被重新写成可重试。 */
    @Test
    void cancellationAfterPrepareRollbackCannotBeRevivedByFailureUpdate() {
        try (var f = new RuntimeReviewFixture(directory.resolve("cancelRollback"))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            f.store.admissionLimits(1, 100, 1000);
            f.runtime.enqueueFixed(
                    f.owner,
                    subscription(f, List.of("7")).reportVersionId(),
                    "XLSX_DATA",
                    Map.of(),
                    "full");
            var serviceRef =
                    new java.util.concurrent.atomic.AtomicReference<ReportSubscriptionService>();
            var intercept =
                    new org.springframework.jdbc.core.JdbcTemplate(f.jdbc.getDataSource()) {
                        /**
                         * 在补偿更新前模拟已提交取消，精确覆盖锁释放后的窗口。
                         *
                         * @param sql 当前契约的sql配置或状态
                         * @param args 当前契约的args配置或状态
                         * @return 当前受控处理的固定结果
                         */
                        @Override
                        public int update(String sql, Object... args) {
                            if (sql.contains("job_id IS NULL")
                                    && sql.startsWith("UPDATE rpt_subscription_run SET"))
                                serviceRef.get().cancel(f.owner, args[args.length - 1].toString());
                            return super.update(sql, args);
                        }
                    };
            var service =
                    new ReportSubscriptionService(
                            resources,
                            intercept,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props);
            serviceRef.set(service);
            var draft =
                    service.create(
                            f.owner, "CANCEL_ROLLBACK", "失败取消", subscription(f, List.of("7")));
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "cancel-before-compensate");
            assertEquals("CANCELLED", service.executions(f.owner, draft.id()).get(0).state());
        }
    }

    /** 通知开关只控制消息发送，不能撤销已发布订阅和私有文件的访问。 */
    @Test
    void disablingNotificationsKeepsSchedulingAndExistingFilesAvailable() throws Exception {
        try (var f = new RuntimeReviewFixture(directory.resolve("notifications"))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            props.setSchedulingEnabled(true);
            props.setNotificationsEnabled(true);
            var service = subscriptions(f, resources, props);
            var base = subscription(f, List.of("7"));
            var model =
                    new SubscriptionDefinition(
                            1,
                            base.reportVersionId(),
                            base.format(),
                            base.parameterRules(),
                            base.recipients(),
                            base.cron(),
                            base.timezone(),
                            base.dstPolicy(),
                            base.catchupSeconds(),
                            true);
            var draft = service.create(f.owner, "NOTIFY_SWITCH", "通知切换", model);
            service.publish(f.owner, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "before-switch");
            var job = f.runtime.jobs(f.owner).get(0);
            complete(f, job);
            props.setNotificationsEnabled(false);
            Instant due = Instant.parse("2026-10-15T01:00:00Z");
            f.jdbc.update(
                    "UPDATE rpt_subscription_schedule SET next_at=?", java.sql.Timestamp.from(due));
            service.dispatchDue(due.plusMillis(1));
            assertTrue(service.schedule(f.owner, draft.id()).enabled());
            assertEquals(2, service.executions(f.owner, draft.id()).size());
            assertEquals("SUCCEEDED", f.runtime.job(f.owner, job.id()).state());
            var deliveries =
                    new ReportDeliveryService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            (tenant, user, id, title) -> fail("开关关闭后不发送通知"));
            deliveries.reconcile();
            assertTrue(
                    deliveries.list(f.owner).stream()
                            .anyMatch(item -> item.state().equals("READY")));
        }
    }

    /**
     * 安装隔离扩展并建立测试用身份约束。
     *
     * @param f 当前契约的f配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportResourceStore extensions(RuntimeReviewFixture f) {
        ReportExtensionSchema.apply(f.jdbc);
        f.store.admissionLimits(50, 100, 1000);
        var resources =
                new ReportResourceStore(
                        f.jdbc, new DataSourceTransactionManager(f.jdbc.getDataSource()), f.store);
        var auth =
                new ReportAuthorizationPort() {
                    /**
                     * 从隔离身份目录解析当前租户的合成用户。
                     *
                     * @param tenant 租户隔离标识
                     * @param user 当前契约的user配置或状态
                     * @return 当前受控操作的固定结果
                     */
                    public ReportActor resolve(String tenant, long user) {
                        return f.actors.get(user);
                    }

                    /**
                     * 验证隔离夹具中合成授权主体是否存在。
                     *
                     * @param tenant 租户隔离标识
                     * @param type 受控类型
                     * @param user 当前契约的user配置或状态
                     * @return 当前受控操作的固定结果
                     */
                    public boolean subjectExists(String tenant, String type, long user) {
                        return f.actors.containsKey(user);
                    }
                };
        f.runtime.constraints(
                new ReportSubscriptionAccessGuard(
                                resources, f.jdbc, auth, f.compiler, f.definitions)
                        ::check);
        return resources;
    }

    /**
     * 组装隔离订阅服务，测试通知始终关闭。
     *
     * @param f 当前契约的f配置或状态
     * @param resources 当前契约的resources配置或状态
     * @param props 当前契约的props配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportSubscriptionService subscriptions(
            RuntimeReviewFixture f,
            ReportResourceStore resources,
            ReportSubscriptionProperties props) {
        return new ReportSubscriptionService(
                resources, f.jdbc, f.store, f.definitions, f.runtime, f.compiler, props);
    }

    /**
     * 生成固定报表版本和UTC时区的合成订阅。
     *
     * @param f 当前契约的f配置或状态
     * @param recipients 独立生成和领取文件的接收人列表
     * @return 当前受控处理的固定结果
     */
    private SubscriptionDefinition subscription(RuntimeReviewFixture f, List<String> recipients) {
        return new SubscriptionDefinition(
                1,
                f.store.definition("A", f.definition.id()).orElseThrow().publishedVersion(),
                "XLSX_DATA",
                Map.of(),
                recipients,
                "0 0 * * * *",
                "UTC",
                "EARLIER_OFFSET",
                3600,
                false);
    }

    /**
     * 按真实领取、快照、布局和输出链路完成隔离导出作业。
     *
     * @param f 当前契约的f配置或状态
     * @param job 当前契约的job配置或状态
     */
    private void complete(RuntimeReviewFixture f, ExportJob job) throws Exception {
        var claimed =
                f.store.claimJob(job, "test-node", Instant.now().plusSeconds(120)).orElseThrow();
        var cancellation = new ReportCancellation();
        var access = f.runtime.buildSnapshot(f.runtime.execution(claimed), cancellation, 5000);
        Path output = f.artifacts.temporary("A", "xlsx");
        try (var stream = f.artifacts.output("A", output)) {
            new ReportExportRenderer(f.runtime, new ReportProperties())
                    .write(claimed, access, stream, cancellation);
        }
        var ref = f.artifacts.commit("A", output, "xlsx");
        assertTrue(
                f.store.finishJob(
                        claimed,
                        claimed.leaseOwner(),
                        "SUCCEEDED",
                        ref,
                        Files.size(f.artifacts.read("A", ref)),
                        null));
    }

    /** 跨午夜恢复只按历史occurrence求解日期，恢复当天不能提前拒绝有效补跑。 */
    @Test
    void catchupValidatesParametersAtScheduledTimeInsteadOfRecoveryClock() {
        try (var f =
                new RuntimeReviewFixture(
                        directory.resolve("catchupClock"),
                        List.of(
                                new DatasetParameter(
                                        "fromDate", "起日", ValueType.DATE, false, "TODAY"),
                                new DatasetParameter(
                                        "toDate", "止日", ValueType.DATE, false, "TODAY")))) {
            var resources = extensions(f);
            var props = new ReportSubscriptionProperties();
            props.setSchedulingEnabled(true);
            var base = f.dsl("CATCHUP_REPORT");
            var dsl =
                    new ReportDsl(
                            1,
                            base.code(),
                            base.title(),
                            base.kind(),
                            base.datasetCode(),
                            base.datasetVersionId(),
                            base.columns(),
                            List.of(
                                    new ReportDsl.Parameter(
                                            "fromDate", "起日", "DATE", false, "TODAY"),
                                    new ReportDsl.Parameter(
                                            "toDate", "止日", "DATE", false, "TODAY")),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            true);
            var report = f.definitions.create(f.owner, dsl, null);
            var version = f.definitions.publish(f.owner, report.id(), report.revision());
            var model =
                    new SubscriptionDefinition(
                            1,
                            version.id(),
                            "XLSX_DATA",
                            Map.of(
                                    "fromDate",
                                    new SubscriptionDefinition.ParameterRule(
                                            "DATE_RELATIVE", "TODAY"),
                                    "toDate",
                                    new SubscriptionDefinition.ParameterRule(
                                            "FIXED", "2026-10-15")),
                            List.of("7"),
                            "0 0 23 * * *",
                            "Asia/Shanghai",
                            "EARLIER_OFFSET",
                            86400,
                            false);
            var initial =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC));
            var draft = initial.create(f.owner, "CATCHUP_SUB", "跨午夜补跑", model);
            initial.publish(f.owner, draft.id(), draft.revision());
            Instant recovered = Instant.parse("2026-10-15T16:30:00Z");
            var restarted =
                    new ReportSubscriptionService(
                            resources,
                            f.jdbc,
                            f.store,
                            f.definitions,
                            f.runtime,
                            f.compiler,
                            props,
                            Clock.fixed(recovered, ZoneOffset.UTC));
            assertEquals(1, restarted.dispatchDue(recovered));
            assertTrue(restarted.schedule(f.owner, draft.id()).enabled());
            var job = f.runtime.jobs(f.owner).get(0);
            var input = f.runtime.payload(f.store.run("A", job.runId()).orElseThrow()).input();
            assertEquals("2026-10-15", input.get("fromDate"));
            assertEquals(input.get("fromDate"), input.get("toDate"));
        }
    }

    /** 授权管理员代发布时，固定来源指纹仍属于原创建者，不应使所有接收人被误拒绝。 */
    @Test
    void delegatedPublisherKeepsOriginalCreatorScopeFingerprint() {
        try (var f = new RuntimeReviewFixture(directory.resolve("delegatedPublisher"))) {
            var resources = extensions(f);
            var service = subscriptions(f, resources, new ReportSubscriptionProperties());
            var draft =
                    service.create(
                            f.owner, "DELEGATED_PUBLISH", "管理员代发布", subscription(f, List.of("7")));
            service.publish(f.root, draft.id(), draft.revision());
            service.manual(f.owner, draft.id(), "delegated");
            assertEquals("QUEUED", service.executions(f.owner, draft.id()).get(0).state());
        }
    }
}
