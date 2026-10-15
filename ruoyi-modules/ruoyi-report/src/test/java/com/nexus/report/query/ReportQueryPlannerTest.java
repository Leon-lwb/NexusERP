package com.nexus.report.query;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.*;
import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 受控目录查询的权限、参数绑定与聚合粒度回归。 */
class ReportQueryPlannerTest {
    /**
     * 构造只有明确部门范围的合成用户。
     *
     * @param permissions 当前契约的permissions配置或状态
     * @return 当前受控处理的固定结果
     */
    private ReportActor actor(Set<String> permissions) {
        return new ReportActor(
                "A",
                7,
                10,
                false,
                List.of(
                        new ReportRole(
                                1, permissions, new ReportScope(false, Set.of(10L), false, 7))));
    }

    /**
     * 构造两实体及唯一批准关系，不使用实际业务表。
     *
     * @return 当前受控处理的固定结果
     */
    private ReportEntityCatalog catalog() {
        var quantity =
                new ReportEntityCatalog.Field(
                        "quantity",
                        "数量",
                        "quantity",
                        ValueType.DECIMAL,
                        "stock:cost",
                        List.of("stock.material", "stock.unit"),
                        true);
        var stock =
                new ReportEntityCatalog.Entity(
                        "stock",
                        "库存",
                        "sample_stock",
                        "stock_id",
                        "stock:list",
                        "tenant_id",
                        "del_flag",
                        "create_dept",
                        "create_by",
                        null,
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "id",
                                        "标识",
                                        "stock_id",
                                        ValueType.ID,
                                        null,
                                        List.of(),
                                        true),
                                new ReportEntityCatalog.Field(
                                        "material",
                                        "物料",
                                        "material_code",
                                        ValueType.STRING,
                                        null,
                                        List.of(),
                                        true),
                                new ReportEntityCatalog.Field(
                                        "unit",
                                        "单位",
                                        "unit",
                                        ValueType.STRING,
                                        null,
                                        List.of(),
                                        true),
                                quantity));
        var material =
                new ReportEntityCatalog.Entity(
                        "material",
                        "物料",
                        "sample_material",
                        "material_id",
                        "material:list",
                        "tenant_id",
                        "del_flag",
                        "create_dept",
                        "create_by",
                        null,
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "code",
                                        "编码",
                                        "material_code",
                                        ValueType.STRING,
                                        null,
                                        List.of(),
                                        true),
                                new ReportEntityCatalog.Field(
                                        "name",
                                        "名称",
                                        "material_name",
                                        ValueType.STRING,
                                        null,
                                        List.of(),
                                        true)));
        return new ReportEntityCatalog(
                1,
                List.of(stock, material),
                List.of(
                        new ReportEntityCatalog.Relation(
                                "stock.material",
                                "stock",
                                "material",
                                "material_code",
                                "material_code",
                                "MANY_TO_ONE",
                                "LEFT")));
    }

    /**
     * 构造客户端只能提交逻辑标识的查询。
     *
     * @return 当前受控处理的固定结果
     */
    private QueryModel model() {
        return new QueryModel(
                1,
                1,
                "stock",
                List.of("stock.material"),
                List.of(
                        new QueryModel.Select("stock.id", "id", null),
                        new QueryModel.Select("material.name", "name", null)),
                List.of(new QueryModel.Filter("stock.material", "EQ", "code")),
                List.of(new QueryModel.Parameter("code", ValueType.STRING, false)),
                List.of(),
                List.of());
    }

    /** 执行leftJoinUsesIndependentPrefilteredScopesAndBoundValues对应的受控处理。 */
    @Test
    void leftJoinUsesIndependentPrefilteredScopesAndBoundValues() {
        var actor = actor(Set.of("stock:list", "material:list"));
        var input = "X' OR 1=1 --";
        var plan = new QueryPlanner(catalog()).plan(model(), actor, Map.of("code", input));
        assertFalse(plan.sql().contains(input));
        assertTrue(plan.values().contains(input));
        assertTrue(plan.sql().contains("LEFT JOIN (SELECT"));
        assertEquals(2, plan.values().stream().filter("A"::equals).count());
        assertTrue(plan.requiredPermissions().containsAll(Set.of("stock:list", "material:list")));
        assertEquals(2, plan.descriptor().fields().size());
    }

    /** 执行missingPermissionOnJoinedEntityOrHiddenFilterFailsClosed对应的受控处理。 */
    @Test
    void missingPermissionOnJoinedEntityOrHiddenFilterFailsClosed() {
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(model(), actor(Set.of("stock:list")), Map.of()));
        var model =
                new QueryModel(
                        1,
                        1,
                        "stock",
                        List.of(),
                        List.of(new QueryModel.Select("stock.id", "id", null)),
                        List.of(new QueryModel.Filter("stock.quantity", "GT", "qty")),
                        List.of(new QueryModel.Parameter("qty", ValueType.DECIMAL, false)),
                        List.of(),
                        List.of());
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(model, actor(Set.of("stock:list")), Map.of("qty", "0")));
    }

    /** 执行aliasesIdentifiersAndUnknownParametersCannotBecomeSql对应的受控处理。 */
    @Test
    void aliasesIdentifiersAndUnknownParametersCannotBecomeSql() {
        var invalid =
                new QueryModel(
                        1,
                        1,
                        "stock; DROP TABLE x",
                        List.of(),
                        model().fields(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(
                                        invalid,
                                        actor(Set.of("stock:list", "material:list")),
                                        Map.of()));
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(
                                        model(),
                                        actor(Set.of("stock:list", "material:list")),
                                        Map.of("undeclared", "x")));
    }

    /** 执行numericAggregatesRequireDeclaredUnitAndMaterialPartitions对应的受控处理。 */
    @Test
    void numericAggregatesRequireDeclaredUnitAndMaterialPartitions() {
        var noDimensions =
                new QueryModel(
                        1,
                        1,
                        "stock",
                        List.of(),
                        List.of(new QueryModel.Select("stock.quantity", "total", "SUM")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(
                                        noDimensions,
                                        actor(Set.of("stock:list", "stock:cost")),
                                        Map.of()));
        var grouped =
                new QueryModel(
                        1,
                        1,
                        "stock",
                        List.of(),
                        List.of(
                                new QueryModel.Select("stock.quantity", "total", "SUM"),
                                new QueryModel.Select("stock.unit", "unit", null),
                                new QueryModel.Select("stock.material", "material", null)),
                        List.of(),
                        List.of(),
                        List.of("stock.unit", "stock.material"),
                        List.of());
        var plan =
                new QueryPlanner(catalog())
                        .plan(grouped, actor(Set.of("stock:list", "stock:cost")), Map.of());
        assertTrue(plan.sql().contains("SUM("));
        assertTrue(plan.sql().contains("GROUP BY"));
    }

    /** 主单据约束细行时，细行不要求不存在的部门字段。 */
    @Test
    void parentOnlyLinePolicyMustNotEraseAuthorizedDepartmentRows() {
        var parent = catalog().entity("stock");
        var child =
                new ReportEntityCatalog.Entity(
                        "line",
                        "细行",
                        "sample_line",
                        "line_id",
                        "stock:list",
                        "tenant_id",
                        "del_flag",
                        null,
                        null,
                        new ReportEntityCatalog.ParentPolicy("stock", "stock_id", "stock_id"),
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "id",
                                        "标识",
                                        "line_id",
                                        ValueType.ID,
                                        null,
                                        List.of(),
                                        true)));
        var cat = new ReportEntityCatalog(1, List.of(parent, child), List.of());
        var query =
                new QueryModel(
                        1,
                        1,
                        "line",
                        List.of(),
                        List.of(new QueryModel.Select("line.id", "id", null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        var plan = new QueryPlanner(cat).plan(query, actor(Set.of("stock:list")), Map.of());
        assertTrue(plan.sql().contains("EXISTS"));
        assertFalse(plan.sql().contains("1=0"));
        assertTrue(plan.values().contains(10L));
    }

    /** 极值也不能混合不同物料和单位。 */
    @Test
    void minMaxRequireDimensionsAndReservedBindingsAreRejected() {
        for (String function : List.of("MIN", "MAX")) {
            var query =
                    new QueryModel(
                            1,
                            1,
                            "stock",
                            List.of(),
                            List.of(new QueryModel.Select("stock.quantity", "extreme", function)),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of());
            assertThrows(
                    ReportFault.class,
                    () ->
                            new QueryPlanner(catalog())
                                    .plan(
                                            query,
                                            actor(Set.of("stock:list", "stock:cost")),
                                            Map.of()));
        }
        for (String alias : List.of("p_code", "row_count", "page_no", "page_count")) {
            var query =
                    new QueryModel(
                            1,
                            1,
                            "stock",
                            List.of(),
                            List.of(new QueryModel.Select("stock.id", alias, null)),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of());
            assertThrows(
                    ReportFault.class,
                    () ->
                            new QueryPlanner(catalog())
                                    .plan(query, actor(Set.of("stock:list")), Map.of()));
        }
    }

    /** 明细敏感字段的范围必须传递到父单据，不能只保留父列表范围。 */
    @Test
    void parentGuardRetainsSensitiveFieldScopeForSelectionFilterAndSort() {
        var parent = catalog().entity("stock");
        var line =
                new ReportEntityCatalog.Entity(
                        "line",
                        "细行",
                        "sample_line",
                        "line_id",
                        "stock:list",
                        "tenant_id",
                        "del_flag",
                        null,
                        null,
                        new ReportEntityCatalog.ParentPolicy("stock", "stock_id", "stock_id"),
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "id", "标识", "line_id", ValueType.ID, null, List.of(), true),
                                new ReportEntityCatalog.Field(
                                        "amount",
                                        "金额",
                                        "amount",
                                        ValueType.DECIMAL,
                                        "stock:cost",
                                        List.of(),
                                        true)));
        var cat = new ReportEntityCatalog(1, List.of(parent, line), List.of());
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(1, Set.of("stock:list"), ReportScope.all(7)),
                                new ReportRole(
                                        2,
                                        Set.of("stock:cost"),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        var query =
                new QueryModel(
                        1,
                        1,
                        "line",
                        List.of(),
                        List.of(new QueryModel.Select("line.amount", "amount", null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        var plan = new QueryPlanner(cat).plan(query, actor, Map.of());
        assertTrue(plan.sql().contains("create_dept"));
        assertTrue(plan.values().contains(10L));
    }

    /** 物料和单位维度必须有原值投影，COUNT不能冒充口径。 */
    @Test
    void groupedCountAliasesCannotReplaceRawAggregationPartitions() {
        var query =
                new QueryModel(
                        1,
                        1,
                        "stock",
                        List.of(),
                        List.of(
                                new QueryModel.Select("stock.material", "material", "COUNT"),
                                new QueryModel.Select("stock.unit", "unit", "COUNT"),
                                new QueryModel.Select("stock.quantity", "quantity", "SUM")),
                        List.of(),
                        List.of(),
                        List.of("stock.material", "stock.unit"),
                        List.of());
        assertThrows(
                ReportFault.class,
                () ->
                        new QueryPlanner(catalog())
                                .plan(query, actor(Set.of("stock:list", "stock:cost")), Map.of()));
        var fields = new ArrayList<>(query.fields());
        fields.add(new QueryModel.Select("stock.material", "rawMaterial", null));
        fields.add(new QueryModel.Select("stock.unit", "rawUnit", null));
        var valid =
                new QueryModel(
                        1,
                        1,
                        "stock",
                        List.of(),
                        fields,
                        List.of(),
                        List.of(),
                        query.groupBy(),
                        List.of());
        assertEquals(
                List.of("rawMaterial", "rawUnit"),
                new QueryPlanner(catalog())
                        .plan(valid, actor(Set.of("stock:list", "stock:cost")), Map.of())
                        .descriptor()
                        .field("quantity")
                        .aggregationKeys());
    }
}
