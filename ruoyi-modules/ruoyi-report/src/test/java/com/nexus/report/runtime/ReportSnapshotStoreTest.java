package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.storage.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** 报表快照存储测试，负责当前模块对应的契约或处理职责。 */
class ReportSnapshotStoreTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 提供者，负责当前模块对应的契约或处理职责。 */
    private static final class Provider implements ReportDatasetProvider {
        /**
         * 读取固定代码契约描述。
         *
         * @return 当前处理返回的固定结果
         */
        public DatasetDescriptor descriptor() {
            return new DatasetDescriptor(
                    "TEST",
                    "测试",
                    1,
                    "wm:stock:list",
                    false,
                    List.of(
                            new DatasetField("id", "ID", ValueType.ID),
                            new DatasetField("label", "编码", ValueType.STRING),
                            new DatasetField("quantity", "数值", ValueType.DECIMAL)),
                    List.of(),
                    List.of("id", "label", "quantity"));
        }

        /**
         * 执行受控只读查询。
         *
         * @param context 服务端执行上下文
         * @param query 已声明字段和筛选参数
         * @return 当前处理返回的固定结果
         */
        public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
            throw new UnsupportedOperationException("stream only");
        }

        /**
         * 在取消与容量控制下逐行读取。
         *
         * @param context 服务端执行上下文
         * @param query 已声明字段和筛选参数
         * @param consume 当前操作的 {@code consume} 输入
         */
        public void stream(
                ReportExecutionContext context,
                DatasetQuery query,
                Consumer<Map<String, Object>> consume) {
            for (int i = 1; i <= 10000; i++) {
                context.cancellation().check();
                consume.accept(
                        Map.of(
                                "id",
                                9007199254740993L + i,
                                "label",
                                "00" + i,
                                "quantity",
                                new BigDecimal("0.123400")));
            }
        }
    }

    /** 验证 {@code largeSnapshotsUseStreamingAndKeepExactWireTypes} 场景下的结果与边界条件。 */
    @Test
    void largeSnapshotsUseStreamingAndKeepExactWireTypes() {
        var provider = new Provider();
        var actor = new ReportActor("A", 1, 10, true, List.of());
        var dsl =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(
                                new ReportDsl.Column("id", "ID", 100, 0, null, null),
                                new ReportDsl.Column("label", "编码", 100, 0, null, null),
                                new ReportDsl.Column("quantity", "数值", 100, 4, null, null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(new ReportDsl.Summary(null, "COUNT_ROWS", List.of(), "条数", 0)),
                        true);
        var report =
                new ReportCompiler(new ObjectMapper()).compile(dsl, provider.descriptor(), actor);
        var store =
                new ReportSnapshotStore(
                        new LocalReportArtifactStore(directory, 20 * 1024 * 1024),
                        new ObjectMapper());
        var snapshot = store.create(report, provider, actor, Map.of(), new ReportCancellation());
        assertEquals(10000, snapshot.rows());
        assertEquals("10000", snapshot.summaries().get(0).value().value());
        var page = store.page("A", snapshot.reference(), 9900, 5);
        assertEquals(5, page.size());
        assertEquals("9007199254750894", page.get(0).get("id").value());
        assertEquals("009901", page.get(0).get("label").value());
        assertEquals("0.123400", page.get(0).get("quantity").value());
    }
}
