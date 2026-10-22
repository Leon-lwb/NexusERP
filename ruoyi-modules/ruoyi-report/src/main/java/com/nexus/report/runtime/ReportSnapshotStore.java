package com.nexus.report.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.*;
import com.nexus.report.storage.ReportArtifactStore;
import com.nexus.report.version.ReportEngineExecutor;
import com.nexus.report.version.V1ReportEngineExecutor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** 逐行 JSON 快照保留精确标识符和十进制值，支持有界分页与流式导出。 */
public final class ReportSnapshotStore {
    /** 保存当前 {@code ROW} 值。 */
    private static final TypeReference<Map<String, TypedValue>> ROW = new TypeReference<>() {};

    /** 保存当前 {@code artifacts} 值。 */
    private final ReportArtifactStore artifacts;

    /** 待处理定义文本。 */
    private final ObjectMapper json;

    /** 保存当前indexes状态或配置。 */
    private ReportSnapshotIndexService indexes;

    /**
     * 只在已安装扩展结构时显式启用块索引，旧快照入口保持兼容。
     *
     * @param indexes 当前契约的indexes配置或状态
     */
    public void indexes(ReportSnapshotIndexService indexes) {
        this.indexes = indexes;
    }

    /**
     * 初始化报表快照存储并保存所需依赖或不可变值。
     *
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param json 待处理定义文本
     */
    public ReportSnapshotStore(ReportArtifactStore artifacts, ObjectMapper json) {
        this.artifacts = artifacts;
        this.json = json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /**
     * 私有精确行快照，流式生成并有界读取，不通过浏览器重新计算业务值。
     *
     * @param reference 私有对象的一次性引用
     * @param checksum 实际内容的SHA-256校验和
     * @param rows 精确类型的数据行
     * @param summaries 当前契约的summaries配置或状态
     */
    public record Snapshot(
            String reference,
            String checksum,
            long rows,
            List<ReportCalculator.SummaryValue> summaries) {}

    /**
     * 创建当前租户有权限的资源。
     *
     * @param compiled 当前操作的 {@code compiled} 输入
     * @param provider 当前操作的 {@code provider} 输入
     * @param actor 当前服务端身份
     * @param parameters 按契约解析的输入参数
     * @param cancellation 执行取消信号
     * @return 当前处理返回的固定结果
     */
    public Snapshot create(
            CompiledReport compiled,
            ReportDatasetProvider provider,
            ReportActor actor,
            Map<String, Object> parameters,
            ReportCancellation cancellation) {
        return create(compiled, provider, actor, parameters, cancellation, 100000);
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param compiled 当前操作的 {@code compiled} 输入
     * @param provider 当前操作的 {@code provider} 输入
     * @param actor 当前服务端身份
     * @param parameters 按契约解析的输入参数
     * @param cancellation 执行取消信号
     * @param maximum 当前操作的 {@code maximum} 输入
     * @return 当前处理返回的固定结果
     */
    public Snapshot create(
            CompiledReport compiled,
            ReportDatasetProvider provider,
            ReportActor actor,
            Map<String, Object> parameters,
            ReportCancellation cancellation,
            int maximum) {
        return create(
                compiled,
                provider,
                actor,
                parameters,
                cancellation,
                maximum,
                new V1ReportEngineExecutor(new ReportCompiler(json)));
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param compiled 当前操作的 {@code compiled} 输入
     * @param provider 当前操作的 {@code provider} 输入
     * @param actor 当前服务端身份
     * @param parameters 按契约解析的输入参数
     * @param cancellation 执行取消信号
     * @param maximum 当前操作的 {@code maximum} 输入
     * @param executor 当前操作的 {@code executor} 输入
     * @return 当前处理返回的固定结果
     */
    public Snapshot create(
            CompiledReport compiled,
            ReportDatasetProvider provider,
            ReportActor actor,
            Map<String, Object> parameters,
            ReportCancellation cancellation,
            int maximum,
            ReportEngineExecutor executor) {
        return create(
                compiled, provider, actor, parameters, cancellation, maximum, executor, rows -> {});
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param compiled 当前操作的 {@code compiled} 输入
     * @param provider 当前操作的 {@code provider} 输入
     * @param actor 当前服务端身份
     * @param parameters 按契约解析的输入参数
     * @param cancellation 执行取消信号
     * @param maximum 当前操作的 {@code maximum} 输入
     * @param executor 当前操作的 {@code executor} 输入
     * @param progress 当前操作的 {@code progress} 输入
     * @return 当前处理返回的固定结果
     */
    public Snapshot create(
            CompiledReport compiled,
            ReportDatasetProvider provider,
            ReportActor actor,
            Map<String, Object> parameters,
            ReportCancellation cancellation,
            int maximum,
            ReportEngineExecutor executor,
            LongConsumer progress) {
        if (maximum < 1 || maximum > 100000) throw ReportFault.capacity();
        cancellation.check();
        int limit =
                compiled.dsl().kind() == ReportDsl.Kind.DOCUMENT
                        ? Math.min(maximum, 5000)
                        : maximum;
        Path temporary = artifacts.temporary(actor.tenantId(), "rows");
        String committed = null;
        long[] count = {0};
        var calculator = executor;
        var accumulator = executor.summaries(compiled);
        AtomicReference<List<ReportCalculator.SummaryValue>> calculatedSummaries =
                new AtomicReference<>();
        try (var writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                artifacts.output(actor.tenantId(), temporary),
                                StandardCharsets.UTF_8))) {
            var context =
                    new ReportExecutionContext(
                            actor, compiled.context(actor).scopes(), cancellation);
            var query =
                    new DatasetQuery(
                            parameters, compiled.requiredFields(), 1, 100001, true, null, false);
            if (compiled.aggregateExpressions()) {
                List<Map<String, Object>> source = new ArrayList<>();
                provider.stream(
                        context,
                        query,
                        row -> {
                            cancellation.check();
                            if (source.size() >= Math.min(5000, limit)) {
                                cancellation.cancel("CAPACITY_EXCEEDED");
                                throw ReportFault.capacity();
                            }
                            source.add(new LinkedHashMap<>(row));
                        });
                var data = calculator.calculate(compiled, source, parameters);
                calculatedSummaries.set(data.summaries());
                for (var row : data.rows()) {
                    cancellation.check();
                    write(writer, row);
                    accumulator.accept(row);
                    count[0]++;
                    if (count[0] % 512 == 0) progress.accept(count[0]);
                }
            } else {
                int cost =
                        compiled.expressions().values().stream()
                                .mapToInt(SafeExpression::cost)
                                .sum();
                provider.stream(
                        context,
                        query,
                        row -> {
                            cancellation.check();
                            if (++count[0] > limit || count[0] * cost > 2_000_000) {
                                cancellation.cancel("CAPACITY_EXCEEDED");
                                throw ReportFault.capacity();
                            }
                            var typed = calculator.calculateRow(compiled, row, parameters);
                            write(writer, typed);
                            accumulator.accept(typed);
                            if (count[0] % 512 == 0) progress.accept(count[0]);
                        });
            }
            progress.accept(count[0]);
            writer.flush();
            cancellation.check();
        } catch (RuntimeException | IOException e) {
            artifacts.discard(actor.tenantId(), temporary);
            if (e instanceof RuntimeException runtime) throw runtime;
            throw storageError();
        }
        try {
            cancellation.check();
            committed = artifacts.commit(actor.tenantId(), temporary, "rows");
            cancellation.check();
            String checksum = artifacts.checksum(actor.tenantId(), committed);
            cancellation.check();
            return new Snapshot(
                    committed,
                    checksum,
                    count[0],
                    calculatedSummaries.get() != null
                            ? calculatedSummaries.get()
                            : accumulator.finish());
        } catch (RuntimeException e) {
            artifacts.discard(actor.tenantId(), temporary);
            if (committed != null) artifacts.delete(actor.tenantId(), committed);
            throw e;
        }
    }

    /**
     * 向受控输出流写入固定报表结果。
     *
     * @param writer 当前操作的 {@code writer} 输入
     * @param row 当前精确业务行
     */
    private void write(BufferedWriter writer, Map<String, TypedValue> row) {
        try {
            String line = json.writeValueAsString(row);
            if (line.length() > 1_000_000) throw ReportFault.capacity();
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            throw storageError();
        }
    }

    /**
     * 读取当前授权范围内的精确分页结果。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param offset 当前操作的 {@code offset} 输入
     * @param size 有界页大小
     * @return 当前处理返回的固定结果
     */
    public List<Map<String, TypedValue>> page(
            String tenant, String reference, long offset, int size) {
        if (offset < 0 || size < 1 || size > 5000)
            throw new ReportFault("INVALID_PARAMETER", "快照分页范围无效");
        if (indexes != null) return indexes.page(tenant, reference, offset, size);
        List<Map<String, TypedValue>> rows = new ArrayList<>();
        long[] count = {0};
        try (var materialized = artifacts.materialize(tenant, reference);
                var reader = Files.newBufferedReader(materialized.path(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (count[0]++ < offset) continue;
                if (rows.size() >= size) break;
                rows.add(parse(line));
            }
            return rows;
        } catch (IOException e) {
            throw storageError();
        }
    }

    /**
     * 完成 {@code each} 对应的报表快照存储处理。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param consume 当前操作的 {@code consume} 输入
     * @param cancellation 执行取消信号
     */
    public void each(
            String tenant,
            String reference,
            Consumer<Map<String, TypedValue>> consume,
            ReportCancellation cancellation) {
        try (var materialized = artifacts.materialize(tenant, reference);
                var reader = Files.newBufferedReader(materialized.path(), StandardCharsets.UTF_8)) {
            String line;
            long count = 0;
            while ((line = reader.readLine()) != null) {
                cancellation.check();
                if (++count > 100000) throw ReportFault.capacity();
                consume.accept(parse(line));
            }
        } catch (IOException e) {
            throw storageError();
        }
    }

    /**
     * 解析并校验受支持的定义结构。
     *
     * @param line 当前操作的 {@code line} 输入
     * @return 当前处理返回的固定结果
     */
    private Map<String, TypedValue> parse(String line) {
        if (line.length() > 1_000_000) throw ReportFault.capacity();
        try {
            return json.readValue(line, ROW);
        } catch (IOException e) {
            throw new ReportFault("SNAPSHOT_INVALID", "快照内容无法解析", 500);
        }
    }

    /**
     * 校验资源绑定与完整性。
     *
     * @param tenant 当前租户标识
     * @param reference 固定私有产物引用
     * @param expected 预期版本或状态
     */
    public void verify(String tenant, String reference, String expected) {
        if (expected == null || !expected.equals(artifacts.checksum(tenant, reference)))
            throw new ReportFault("SNAPSHOT_INVALID", "快照完整性校验失败", 500);
    }

    /**
     * 完成 {@code storageError} 对应的报表快照存储处理。
     *
     * @return 当前处理返回的固定结果
     */
    private static ReportFault storageError() {
        return new ReportFault("ARTIFACT_STORE_UNAVAILABLE", "报表快照读写失败", 500);
    }
}
