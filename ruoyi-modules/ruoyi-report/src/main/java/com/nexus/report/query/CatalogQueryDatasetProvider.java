package com.nexus.report.query;

import com.nexus.common.report.*;

import java.util.*;
import java.util.function.Consumer;

/** 固定查询发布的数据提供者，不跟随查询草稿或最新目录变化。 */
public final class CatalogQueryDatasetProvider implements ReportDatasetProvider {
    /** 保存当前planner状态或配置。 */
    private final QueryPlanner planner;

    /** 保存当前model状态或配置。 */
    private final QueryModel model;

    /** 保存当前descriptor状态或配置。 */
    private final DatasetDescriptor descriptor;

    /** 保存当前executor状态或配置。 */
    private final CatalogQueryExecutor executor;

    /** 保存当前permissions状态或配置。 */
    private final Set<String> permissions;

    /** 保存当前identity状态或配置。 */
    private final String identity;

    /**
     * 绑定版本、执行账户和完整依赖，并检查当前所有业务权限。
     *
     * @param planner 当前契约的planner配置或状态
     * @param model 当前契约的model配置或状态
     * @param descriptor 当前契约的descriptor配置或状态
     * @param executor 当前契约的executor配置或状态
     * @param identity 当前契约的identity配置或状态
     * @param actor 执行用户
     */
    public CatalogQueryDatasetProvider(
            QueryPlanner planner,
            QueryModel model,
            DatasetDescriptor descriptor,
            CatalogQueryExecutor executor,
            String identity,
            ReportActor actor) {
        this.planner = planner;
        this.model = model;
        this.descriptor = descriptor;
        this.executor = executor;
        this.identity = identity;
        permissions = planner.validate(model, actor).requiredPermissions();
    }

    /**
     * 返回发布时保存的精确契约。
     *
     * @return 当前受控处理的固定结果
     */
    @Override
    public DatasetDescriptor descriptor() {
        return descriptor;
    }

    /**
     * 返回查询与目录发布的联合完整性身份。
     *
     * @return 当前受控处理的固定结果
     */
    @Override
    public String executionIdentity() {
        return identity;
    }

    /**
     * 返回隐藏条件、关系和数值字段涉及的全部业务权限。
     *
     * @return 当前受控处理的固定结果
     */
    @Override
    public Set<String> requiredPermissions() {
        return permissions;
    }

    /**
     * 每次取数重新规划当前实体范围，外部排序仅接受输出别名。
     *
     * @param context 当前契约的context配置或状态
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    private QueryPlanner.Plan plan(ReportExecutionContext context, DatasetQuery input) {
        var validated = DatasetQueryValidator.validate(descriptor, input);
        var plan = planner.plan(model, context.actor(), validated.parameters());
        String sql = plan.sql();
        if (validated.sortField() != null) {
            String field = validated.sortField();
            sql =
                    "SELECT * FROM ("
                            + sql
                            + ") report_projection ORDER BY `"
                            + field
                            + "`"
                            + (validated.descending() ? " DESC" : " ASC");
        }
        return new QueryPlanner.Plan(
                sql, plan.values(), descriptor, plan.requiredPermissions(), plan.tables());
    }

    /**
     * 分页与总数在同一独立只读事务中取得。
     *
     * @param context 当前契约的context配置或状态
     * @param input 已规范化的参数值
     * @return 当前受控处理的固定结果
     */
    @Override
    public DatasetResult query(ReportExecutionContext context, DatasetQuery input) {
        return executor.query(plan(context, input), context, input);
    }

    /**
     * 使用同一受控规划流式构建固定快照。
     *
     * @param context 当前契约的context配置或状态
     * @param input 已规范化的参数值
     * @param consumer 当前契约的consumer配置或状态
     */
    @Override
    public void stream(
            ReportExecutionContext context,
            DatasetQuery input,
            Consumer<Map<String, Object>> consumer) {
        executor.stream(plan(context, input), context, input, consumer);
    }
}
