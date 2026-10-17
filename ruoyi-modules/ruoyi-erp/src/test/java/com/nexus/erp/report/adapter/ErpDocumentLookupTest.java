package com.nexus.erp.report.adapter;

import static com.nexus.erp.report.adapter.FoundationReportFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 验证单据参数候选使用实际主键并遵守完整权限、租户和部门范围。 */
class ErpDocumentLookupTest {
    /** 验证销售和收货单的固定候选规则、完整长整型ID回显与输入绑定。 */
    @Test
    void documentLookupsUseFixedHeaderKeysAndPreserveFullIds() throws Exception {
        var fixture = new FoundationReportFixture("so_sales_order_header", "wm_material_receive");
        seed(fixture);
        var provider = new ErpReportLookupProvider(fixture.jdbc);
        for (var binding : bindings()) {
            assertTrue(provider.bindings().contains(binding), "缺少新单据固定候选：" + binding.datasetCode());
            var allowed = context(binding.permission());
            for (String keyword : List.of("SYNTHETIC", "合成", Long.toString(Long.MAX_VALUE))) {
                var result = provider.query(
                        binding, allowed, new ReportLookupProvider.Query(keyword, 1, 20));
                assertEquals(1, result.total());
                assertEquals(Long.toString(Long.MAX_VALUE), result.options().get(0).value());
            }
            assertTrue(provider.selected(binding, allowed, Long.toString(Long.MAX_VALUE)).isPresent());
            assertEquals(
                    0,
                    provider.query(binding, allowed,
                                    new ReportLookupProvider.Query("%' OR 1=1 --", 1, 20))
                            .total());
            for (String invisible : List.of("2", "3", "4")) {
                assertTrue(provider.selected(binding, allowed, invisible).isEmpty());
            }
            assertThrows(DatasetAccessException.class,
                    () -> provider.query(binding, context(), new ReportLookupProvider.Query("", 1, 20)));
            var narrowed = new ReportExecutionContext(allowed.actor(),
                    List.of(new ReportScope(false, Set.of(20L), false, 7)));
            assertTrue(provider.selected(binding, narrowed, Long.toString(Long.MAX_VALUE)).isEmpty());
        }
    }

    /**
     * 定义新单据版本一必须安装的固定选择器契约。
     *
     * @return 销售订单和采购收货候选规则
     */
    private List<ReportLookupBinding> bindings() {
        return List.of(
                new ReportLookupBinding(
                        "ERP_SALES_ORDER_DOCUMENT", 1, "orderId", "销售订单", "so:orderheader:query"),
                new ReportLookupBinding(
                        "ERP_PURCHASE_RECEIVE_DOCUMENT", 1, "receiveId", "采购收货单", "wm:receive:query"));
    }

    /**
     * 插入同租户可见、其他部门、其他租户和已删除的合成单据。
     *
     * @param fixture 隔离H2数据库
     */
    private void seed(FoundationReportFixture fixture) {
        for (int index = 0; index < 4; index++) {
            long identifier = index == 0 ? Long.MAX_VALUE : index + 1;
            fixture.insert("so_sales_order_header", "so_id", identifier, "so_no", "SO-SYNTHETIC",
                    "client_name", "合成客户", "create_dept", index == 1 ? 20 : 10,
                    "tenant_id", index == 2 ? "B" : "A", "del_flag", index == 3 ? "1" : "0");
            fixture.insert("wm_material_receive", "receive_id", identifier, "receive_code", "RC-SYNTHETIC",
                    "receive_name", "合成收货单", "vendor_name", "合成供应商", "create_dept", index == 1 ? 20 : 10,
                    "tenant_id", index == 2 ? "B" : "A", "del_flag", index == 3 ? "1" : "0");
        }
    }
}
