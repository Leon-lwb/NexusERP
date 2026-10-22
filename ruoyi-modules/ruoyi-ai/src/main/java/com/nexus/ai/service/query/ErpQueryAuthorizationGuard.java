package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.report.ReportActor;
import com.nexus.common.report.ReportAuthorizationPort;
import com.nexus.common.report.ReportScope;
import com.nexus.common.report.catalog.ReportEntityCatalog;
import com.nexus.common.report.catalog.ReportEntityCatalogProvider;
import com.nexus.common.satoken.utils.LoginHelper;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.MultiPartName;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JDBC 最终授权边界，统一覆盖生成 SQL、固定工具与 Flow 查询。
 * 每张物理表先转换为租户、业务范围及字段受控的投影，再参与用户查询。
 * 每次重读服务端身份；登录角色缓存及表存在性均不能作为授权依据。
 */
@Component
public class ErpQueryAuthorizationGuard {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern TENANT = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    // 在最终边界保留已有 SQL 安全限制；固定工具不会调用上层校验器。
    private static final Pattern FORBIDDEN = Pattern.compile(
        "\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD_FILE|LOAD\\s+DATA|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|"
            + "GRANT|REVOKE|EXECUTE|INFORMATION_SCHEMA|SLEEP|BENCHMARK|WAITFOR)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> FUNCTIONS = Set.of("count", "sum", "avg", "min", "max", "coalesce", "ifnull",
        "nullif", "round", "abs", "ceil", "ceiling", "floor", "lower", "upper", "concat", "length", "char_length",
        "trim", "substring", "substr", "date_format", "year", "month", "day", "date", "curdate", "now",
        "date_sub", "date_add", "datediff", "greatest", "least");
    private static final Set<String> BINARY = Set.of("AndExpression", "OrExpression", "EqualsTo", "NotEqualsTo",
        "GreaterThan", "GreaterThanEquals", "MinorThan", "MinorThanEquals", "Addition", "Subtraction",
        "Multiplication", "Division", "IntegerDivision", "Modulo", "LikeExpression");
    private final ReportAuthorizationPort authorization;
    private final List<ReportEntityCatalogProvider> providers;
    private final AiProperties properties;

    public ErpQueryAuthorizationGuard(ReportAuthorizationPort authorization,
                                      List<ReportEntityCatalogProvider> providers, AiProperties properties) {
        this.authorization = Objects.requireNonNull(authorization);
        this.providers = List.copyOf(providers);
        this.properties = Objects.requireNonNull(properties);
    }

    /** 不使用缓存角色/权限，不转换租户数字，不在后台线程回退登录身份。 */
    public ReportActor currentActor() {
        String tenant = AiRequestContext.getTenantId();
        Long user = AiRequestContext.getUserId();
        boolean contextPresent = tenant != null || user != null;
        if (contextPresent && (tenant == null || user == null)) throw identityDenied();
        if (RequestContextHolder.getRequestAttributes() != null) {
            if (!LoginHelper.isLogin()) throw identityDenied();
            String loginTenant = LoginHelper.getTenantId();
            Long loginUser = LoginHelper.getUserId();
            if (contextPresent && (!Objects.equals(tenant, loginTenant) || !Objects.equals(user, loginUser)))
                throw identityDenied();
            tenant = loginTenant;
            user = loginUser;
        }
        if (tenant == null || !TENANT.matcher(tenant).matches() || user == null || user <= 0) throw identityDenied();
        ReportActor actor;
        try {
            // 系统适配器通过 @DS("master") 重读当前账户、租户、套餐与角色。
            actor = authorization.resolve(tenant, user);
        } catch (RuntimeException failure) { throw identityDenied(); }
        if (actor == null || !tenant.equals(actor.tenantId()) || user.longValue() != actor.userId()) throw identityDenied();
        return actor;
    }

    public record AuthorizedQuery(String sql, boolean hideExplain) { }

    public AuthorizedQuery authorize(String sql) {
        ReportActor actor = currentActor();
        if (sql == null || sql.isBlank() || sql.length() > 100_000) throw unsupported();
        // 拒绝 MySQL 可执行注释和优化器提示；上层既有校验器会清理普通注释。
        if (sql.contains("/*") || sql.contains("--") || sql.contains("#") || FORBIDDEN.matcher(sql).find()) throw unsupported();
        String selectSql = sql.trim();
        boolean explain = selectSql.regionMatches(true, 0, "EXPLAIN ", 0, 8);
        if (explain) selectSql = selectSql.substring(8).stripLeading();
        if (!selectSql.regionMatches(true, 0, "SELECT", 0, 6)
            && !(actor.superAdmin() && selectSql.regionMatches(true, 0, "WITH ", 0, 5))) throw unsupported();
        try {
            var statements = CCJSqlParserUtil.parseStatements(selectSql).getStatements();
            if (statements.size() != 1) throw unsupported();
            var statement = statements.get(0);
            if (!(statement instanceof Select select)) throw unsupported();
            new QueryPolicy(actor, actor.superAdmin() ? null : catalog()).select(select, null, 0);
            return new AuthorizedQuery((explain ? "EXPLAIN " : "") + select, explain && !actor.superAdmin());
        } catch (IllegalArgumentException failure) { throw failure;
        } catch (Exception failure) { throw unsupported(); }
    }

    public int maxRows(int requested) {
        int ceiling = properties.getSqlMaxRows();
        if (ceiling <= 0) throw unsupported();
        return requested > 0 ? Math.min(requested, ceiling) : ceiling;
    }

    public int timeoutSeconds(int requested) {
        int ceiling = Math.max(1, properties.getSqlTimeout() / 1000);
        return requested > 0 ? Math.min(requested, ceiling) : ceiling;
    }

    public boolean canReadTable(ReportActor actor, String name) {
        if (!allowedTable(name)) return false;
        if (actor.superAdmin()) return true;
        var catalog = catalog();
        var entity = catalog.byTable.get(normalize(name));
        if (entity == null || !actor.has(entity.permission()) || approvedFields(actor, entity).isEmpty()) return false;
        new QueryPolicy(actor, catalog).predicate(entity, "__ai_metadata", List.of(actor.scopeFor(entity.permission())), new HashSet<>());
        return true;
    }

    public List<String> visibleTables(ReportActor actor) {
        return catalog().byTable.values().stream().map(ReportEntityCatalog.Entity::table)
            .filter(name -> canReadTable(actor, name)).sorted().toList();
    }

    /** 受限身份仅获取目录批准字段的结构，不返回原始 DDL。 */
    public String tableSchema(ReportActor actor, String name) {
        if (!canReadTable(actor, name) || actor.superAdmin()) throw denied();
        var entity = catalog().byTable.get(normalize(name));
        return "CREATE TABLE `" + entity.table() + "` (\n" + approvedFields(actor, entity).stream()
            .map(field -> "  `" + field.column() + "` " + switch (field.type()) {
                case ID, INTEGER -> "BIGINT";
                case DECIMAL -> "DECIMAL";
                case BOOLEAN -> "BOOLEAN";
                case DATE -> "DATE";
                case DATETIME -> "TIMESTAMP";
                case STRING -> "VARCHAR";
            }).collect(Collectors.joining(",\n")) + "\n)";
    }

    private boolean allowedTable(String name) {
        var prefixes = properties.getAllowedTablePrefixes();
        if (prefixes == null || prefixes.isEmpty() || prefixes.stream().anyMatch(prefix -> prefix == null
            || !IDENTIFIER.matcher(prefix).matches())) throw unsupported();
        return name != null && IDENTIFIER.matcher(name).matches()
            && prefixes.stream().anyMatch(prefix -> normalize(name).startsWith(normalize(prefix)));
    }

    /** 供统一元数据投影层读取可信目录；不读取原始缓存中的注释、默认值或外键。 */
    public ReportEntityCatalog.Entity metadataEntity(ReportActor actor, String table) {
        if (actor.superAdmin() || !canReadTable(actor, table)) throw denied();
        var entity = catalog().byTable.get(normalize(table));
        return new ReportEntityCatalog.Entity(entity.id(), entity.label(), entity.table(), entity.key(), entity.permission(),
            entity.tenantColumn(), entity.deleteColumn(), entity.departmentColumn(), entity.ownerColumn(), entity.parent(),
            approvedFields(actor, entity));
    }

    private Catalog catalog() {
        Map<String, ReportEntityCatalog.Entity> byTable = new LinkedHashMap<>();
        Map<String, ReportEntityCatalog.Entity> byId = new LinkedHashMap<>();
        for (var provider : providers) {
            var fixed = provider.catalog();
            if (fixed == null) throw denied();
            for (var entity : fixed.entities()) {
                if (byTable.putIfAbsent(normalize(entity.table()), entity) != null
                    || byId.putIfAbsent(entity.id(), entity) != null) throw denied();
            }
        }
        if (byTable.isEmpty()) throw denied();
        return new Catalog(byTable, byId);
    }

    private record Catalog(Map<String, ReportEntityCatalog.Entity> byTable, Map<String, ReportEntityCatalog.Entity> byId) { }

    private static List<ReportEntityCatalog.Field> approvedFields(ReportActor actor, ReportEntityCatalog.Entity entity) {
        return entity.fields().stream().filter(field -> field.permission() == null || actor.has(field.permission())).toList();
    }

    private final class QueryPolicy {
        private final ReportActor actor;
        private final Catalog catalog;
        private QueryPolicy(ReportActor actor, Catalog catalog) { this.actor = actor; this.catalog = catalog; }

        private Set<String> select(Select select, Scope outer, int depth) {
            return select(select, outer, depth, outer == null ? Set.of() : outer.ctes, false);
        }

        private Set<String> select(Select select, Scope outer, int depth, Set<String> availableCtes, boolean recursiveBody) {
            Set<String> ctes = new LinkedHashSet<>(availableCtes);
            if (nonempty(select.getWithItemsList())) {
                if (!actor.superAdmin()) throw unsupported();
                for (var item : select.getWithItemsList()) {
                    String name = item.getUnquotedAliasName();
                    if (name == null || !IDENTIFIER.matcher(name).matches() || ctes.contains(normalize(name))
                        || item.isMaterialized() || item.getSelect() == null) throw unsupported();
                    if (item.getWithItemList() != null) for (var label : item.getWithItemList()) {
                        if (!(label.getExpression() instanceof Column column) || column.getTable() != null
                            && column.getTable().getName() != null || label.getAlias() != null
                            || !IDENTIFIER.matcher(column.getColumnName()).matches()) throw unsupported();
                    }
                    if (item.isRecursive()) ctes.add(normalize(name));
                    select(item.getSelect(), null, depth + 1, ctes, item.isRecursive());
                    ctes.add(normalize(name));
                }
            }
            if (depth > 16 || select.getForMode() != null
                || select.getForClause() != null || select.getForUpdateTable() != null || select.getWait() != null
                || select.getIsolation() != null || select.isSkipLocked() || select.isNoWait()
                || select.getPivot() != null || select.getUnPivot() != null || select.getFetch() != null
                || select.getLimitBy() != null) throw unsupported();
            if (select instanceof ParenthesedSelect parenthesed) {
                // 括号层可单独携带排序/分页，其中可能藏有未遍历的子查询；首期仅支持内层 SELECT 的子句。
                if (parenthesed.getSampleClause() != null || nonempty(parenthesed.getOrderByElements())
                    || parenthesed.getLimit() != null || parenthesed.getOffset() != null) throw unsupported();
                return select(parenthesed.getSelect(), outer, depth + 1, ctes, recursiveBody);
            }
            if (select instanceof SetOperationList set) {
                // 固定 BOM 工具使用递归 UNION ALL；每个分支仍独立保护所有物理表。
                if (!actor.superAdmin() || !recursiveBody || nonempty(set.getOrderByElements()) || set.getLimit() != null
                    || set.getOffset() != null || set.getSampleClause() != null || set.getOperations().stream()
                    .anyMatch(operation -> !(operation instanceof UnionOp union) || !union.isAll())) throw unsupported();
                Set<String> output = Set.of();
                for (var branch : set.getSelects()) {
                    var branchOutput = select(branch, outer, depth + 1, ctes, false);
                    if (output.isEmpty()) output = branchOutput;
                }
                return output;
            }
            if (!(select instanceof PlainSelect plain)) throw unsupported();
            if (nonempty(plain.getIntoTables()) || plain.getIntoTempTable() != null || nonempty(plain.getLateralViews())
                || plain.getSampleClause() != null || plain.getOptimizeFor() != null || plain.getTop() != null
                || plain.getSkip() != null || plain.getFirst() != null || plain.getQualify() != null
                || plain.getOracleHierarchical() != null || plain.getPreferringClause() != null
                || plain.getOracleHint() != null || plain.getForXmlPath() != null || plain.getKsqlWindow() != null
                || nonempty(plain.getWindowDefinitions()) || plain.isEmitChanges() || plain.isUsingFinal()
                || plain.isUsingOnly() || plain.isUseWithNoLog() || plain.getMySqlSqlCalcFoundRows()
                || plain.getMySqlSqlCacheFlag() != null || plain.getMySqlHintStraightJoin()
                || plain.getBigQuerySelectQualifier() != null) throw unsupported();
            if (plain.getDistinct() != null && nonempty(plain.getDistinct().getOnSelectItems())) throw unsupported();
            var scope = new Scope(outer, ctes);
            if (plain.getFromItem() != null) bind(plain.getFromItem(), scope, depth);
            if (plain.getJoins() != null) {
                for (var join : plain.getJoins()) {
                    if (join.isNatural() || join.isApply() || join.isSemi() || join.isGlobal() || join.isWindowJoin()
                        || join.isStraight() || join.getJoinHint() != null || nonempty(join.getUsingColumns())) throw unsupported();
                    bind(join.getRightItem(), scope, depth);
                }
                for (var join : plain.getJoins()) for (var on : join.getOnExpressions()) expression(on, scope, true, false, depth);
            }
            if (plain.getSelectItems() == null || plain.getSelectItems().isEmpty()) throw unsupported();
            List<SelectItem<?>> projection = new ArrayList<>();
            Set<String> output = new LinkedHashSet<>();
            for (var item : plain.getSelectItems()) {
                if (!actor.superAdmin() && item.getExpression() instanceof AllColumns star) {
                    if (nonempty(star.getExceptColumns()) || nonempty(star.getReplaceExpressions())) throw unsupported();
                    var bindings = star instanceof AllTableColumns tableStar
                        ? List.of(scope.binding(tableStar.getTable().getName())) : new ArrayList<>(scope.bindings.values());
                    if (bindings.isEmpty()) throw unsupported();
                    for (var binding : bindings) for (var column : binding.visibleColumns()) {
                        binding.use(column, false);
                        projection.add(SelectItem.from(new Column(binding.alias + "." + quote(column))));
                        output.add(normalize(column));
                    }
                } else {
                    expression(item.getExpression(), scope, false, false, depth);
                    projection.add(item);
                    String label = item.getAlias() == null ? item.getExpression() instanceof Column column
                        ? column.getColumnName() : null : item.getAlias().getName();
                    if (label != null) {
                        label = normalize(MultiPartName.unquote(label));
                        output.add(label);
                        if (item.getAlias() != null) scope.outputAliases.add(label);
                    }
                }
            }
            plain.setSelectItems(projection);
            expression(plain.getWhere(), scope, true, false, depth);
            if (plain.getGroupBy() != null) {
                if (nonempty(plain.getGroupBy().getGroupingSets()) || plain.getGroupBy().isMysqlWithRollup()) throw unsupported();
                expression(plain.getGroupBy().getGroupByExpressionList(), scope, false, true, depth);
            }
            expression(plain.getHaving(), scope, false, true, depth);
            if (plain.getOrderByElements() != null) for (var order : plain.getOrderByElements())
                expression(order.getExpression(), scope, false, true, depth);
            if (plain.getLimit() != null) {
                if (plain.getLimit().isLimitAll() || plain.getLimit().isLimitNull() || nonempty(plain.getLimit().getByExpressions())) throw unsupported();
                limit(plain.getLimit().getOffset());
                limit(plain.getLimit().getRowCount());
            }
            if (plain.getOffset() != null) limit(plain.getOffset().getOffset());
            if (plain.getFromItem() != null) plain.setFromItem(protect(plain.getFromItem(), scope));
            if (plain.getJoins() != null) for (var join : plain.getJoins()) join.setRightItem(protect(join.getRightItem(), scope));
            return output;
        }

        private void bind(FromItem from, Scope scope, int depth) {
            if (from.getPivot() != null || from.getUnPivot() != null) throw unsupported();
            if (from.getAlias() != null && nonempty(from.getAlias().getAliasColumns())) throw unsupported();
            Binding binding;
            if (from instanceof Table table) {
                if (table.getNameParts().size() != 1 || table.getIndexHint() != null || table.getSqlServerHints() != null
                    || table.getSampleClause() != null) throw unsupported();
                String name = MultiPartName.unquote(table.getName());
                boolean cte = scope.ctes.contains(normalize(name));
                if (!cte && !allowedTable(name)) throw denied();
                String alias = from.getAlias() == null ? name : MultiPartName.unquote(from.getAlias().getName());
                if (!IDENTIFIER.matcher(alias).matches()) throw unsupported();
                var entity = actor.superAdmin() ? null : catalog.byTable.get(normalize(name));
                if (!actor.superAdmin() && (entity == null || !actor.has(entity.permission()))) throw denied();
                binding = new Binding(alias, name, entity, null);
                binding.cte = cte;
            } else if (from instanceof ParenthesedSelect derived) {
                if (from.getAlias() == null || !IDENTIFIER.matcher(from.getAlias().getName()).matches()) throw unsupported();
                // 普通派生表不能读取外层绑定；尚不支持横向派生表。
                binding = new Binding(from.getAlias().getName(), null, null, select(derived, null, depth + 1, scope.ctes, false));
            } else throw unsupported();
            if (scope.bindings.putIfAbsent(normalize(binding.alias), binding) != null) throw unsupported();
        }

        private FromItem protect(FromItem from, Scope scope) {
            if (!(from instanceof Table table)) return from;
            String alias = from.getAlias() == null ? MultiPartName.unquote(table.getName()) : MultiPartName.unquote(from.getAlias().getName());
            var binding = scope.binding(alias);
            if (binding.cte) return from;
            String projection;
            String predicate;
            if (actor.superAdmin()) {
                projection = "*";
                predicate = quote("tenant_id") + "='" + actor.tenantId() + "'";
            } else {
                Set<String> columns = new LinkedHashSet<>(binding.usedColumns);
                // WHERE/JOIN 可引用固定控制列；输出通配符始终排除这些列。
                columns.add(binding.entity.tenantColumn());
                if (binding.entity.deleteColumn() != null) columns.add(binding.entity.deleteColumn());
                projection = columns.stream().map(ErpQueryAuthorizationGuard::quote).collect(Collectors.joining(","));
                predicate = predicate(binding.entity, "__ai_row", binding.permissions.stream().map(actor::scopeFor).toList(), new HashSet<>());
            }
            try {
                String sourceAlias = actor.superAdmin() ? "" : " __ai_row";
                return ((Select) CCJSqlParserUtil.parse("SELECT * FROM (SELECT " + projection + " FROM "
                    + quote(binding.entity == null ? binding.table : binding.entity.table()) + sourceAlias + " WHERE " + predicate
                    + ") AS " + quote(binding.alias))).getPlainSelect().getFromItem();
            } catch (Exception failure) { throw unsupported(); }
        }

        private String predicate(ReportEntityCatalog.Entity entity, String alias, List<ReportScope> scopes, Set<String> visiting) {
            if (!visiting.add(entity.id()) || visiting.size() > 8 || !actor.has(entity.permission())) throw denied();
            String prefix = quote(alias) + ".";
            List<String> clauses = new ArrayList<>();
            clauses.add(prefix + quote(entity.tenantColumn()) + "='" + actor.tenantId() + "'");
            if (entity.deleteColumn() != null) clauses.add(prefix + quote(entity.deleteColumn()) + "='0'");
            if (entity.parent() != null) {
                var parentPolicy = entity.parent();
                var parent = catalog.byId.get(parentPolicy.entity());
                if (parent == null || !IDENTIFIER.matcher(parentPolicy.childColumn()).matches()
                    || !IDENTIFIER.matcher(parentPolicy.parentColumn()).matches()) throw denied();
                List<ReportScope> parentScopes = new ArrayList<>(scopes);
                parentScopes.add(actor.scopeFor(parent.permission()));
                String parentAlias = "__ai_parent_" + visiting.size();
                clauses.add("EXISTS (SELECT 1 FROM " + quote(parent.table()) + " " + quote(parentAlias)
                    + " WHERE " + quote(parentAlias) + "." + quote(parentPolicy.parentColumn()) + "=" + prefix + quote(parentPolicy.childColumn())
                    + " AND " + predicate(parent, parentAlias, parentScopes, visiting) + ")");
            } else {
                for (var scope : scopes) {
                    if (scope.userId() != actor.userId() || scope.departments().size() > 2000) throw denied();
                    if (scope.all()) continue;
                    List<String> choices = new ArrayList<>();
                    if (!scope.departments().isEmpty()) {
                        if (entity.departmentColumn() == null || scope.departments().stream().anyMatch(id -> id <= 0)) throw denied();
                        choices.add(prefix + quote(entity.departmentColumn()) + " IN ("
                            + scope.departments().stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
                    }
                    if (scope.self()) {
                        if (entity.ownerColumn() == null) throw denied();
                        choices.add(prefix + quote(entity.ownerColumn()) + "=" + actor.userId());
                    }
                    clauses.add(choices.isEmpty() ? "1=0" : "(" + String.join(" OR ", choices) + ")");
                }
            }
            visiting.remove(entity.id());
            return String.join(" AND ", clauses);
        }

        /** 显式表达式白名单：新增解析节点不能隐式隐藏字段或物理表访问。 */
        private void expression(Expression expression, Scope scope, boolean controls, boolean aliases, int depth) {
            if (expression == null) return;
            if (depth > 64) throw unsupported();
            if (expression instanceof Column column) {
                // 列下标另存于 Column，可能包含未授权字段或子查询；首期不支持数组访问。
                if (column.getArrayConstructor() != null) throw unsupported();
                String name = MultiPartName.unquote(column.getColumnName());
                if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]{0,63}")) throw unsupported();
                if (column.getTable() != null && column.getTable().getName() != null) {
                    if (column.getTable().getNameParts().size() != 1) throw unsupported();
                    scope.binding(column.getTable().getName()).use(name, controls);
                } else if (aliases && scope.outputAliases.contains(normalize(name))) scope.aliasColumn(name, controls);
                else scope.column(name, controls);
            } else if (expression instanceof JdbcParameter parameter) {
                if (parameter.isUseFixedIndex() || !"?".equals(parameter.getParameterCharacter())) throw unsupported();
            } else if (expression instanceof LongValue || expression instanceof DoubleValue || expression instanceof StringValue
                || expression instanceof NullValue || expression instanceof DateValue || expression instanceof TimeValue
                || expression instanceof TimestampValue) {
                // 类型化字面量不含字段引用或可执行 SQL。
            } else if (expression instanceof BinaryExpression binary && BINARY.contains(expression.getClass().getSimpleName())) {
                expression(binary.getLeftExpression(), scope, controls, aliases, depth + 1);
                expression(binary.getRightExpression(), scope, controls, aliases, depth + 1);
                if (binary instanceof LikeExpression like) expression(like.getEscape(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof ExpressionList<?> list) {
                for (var item : list) expression(item, scope, controls, aliases, depth + 1);
            } else if (expression instanceof InExpression in) {
                expression(in.getLeftExpression(), scope, controls, aliases, depth + 1);
                expression(in.getRightExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof Between between) {
                expression(between.getLeftExpression(), scope, controls, aliases, depth + 1);
                expression(between.getBetweenExpressionStart(), scope, controls, aliases, depth + 1);
                expression(between.getBetweenExpressionEnd(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof IsNullExpression isNull) {
                expression(isNull.getLeftExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof IsBooleanExpression isBoolean) {
                expression(isBoolean.getLeftExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof NotExpression not) {
                expression(not.getExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof SignedExpression signed) {
                expression(signed.getExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof ExistsExpression exists) {
                expression(exists.getRightExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof Select subquery) {
                select(subquery, scope, depth + 1);
            } else if (expression instanceof Function function) {
                String name = normalize(function.getName());
                if (!FUNCTIONS.contains(name) || function.getMultipartName().size() != 1 || function.getAttribute() != null
                    || function.getAttributeColumn() != null || function.getKeep() != null || function.getNamedParameters() != null
                    || function.getHavingClause() != null || function.getLimit() != null || nonempty(function.getOrderByElements())
                    || function.getExtraKeyword() != null || function.getNullHandling() != null) throw unsupported();
                if (function.getParameters() != null) for (var parameter : function.getParameters()) {
                    if (parameter instanceof AllColumns star && star.getClass() == AllColumns.class && "count".equals(name)) {
                        if (nonempty(star.getExceptColumns()) || nonempty(star.getReplaceExpressions())) throw unsupported();
                        continue;
                    }
                    expression(parameter, scope, controls, aliases, depth + 1);
                }
                if (function.isAllColumns() && !"count".equals(name)) throw unsupported();
            } else if (expression instanceof MySQLGroupConcat groupConcat) {
                // GROUP_CONCAT 的输入与内部排序都引用原始业务列，不能借输出别名跳过字段范围。
                if (!nonempty(groupConcat.getExpressionList())) throw unsupported();
                expression(groupConcat.getExpressionList(), scope, controls, false, depth + 1);
                if (groupConcat.getOrderByElements() != null) for (var order : groupConcat.getOrderByElements())
                    expression(order.getExpression(), scope, controls, false, depth + 1);
                if (groupConcat.getSeparator() != null) {
                    // 解析器以原文字符串保存分隔符，仅接受完整的普通字符串字面量。
                    String separator = groupConcat.getSeparator();
                    try {
                        var parsed = CCJSqlParserUtil.parseExpression(separator);
                        if (!(parsed instanceof StringValue literal) || literal.getPrefix() != null
                            || !literal.toString().equals(separator)) throw unsupported();
                    } catch (Exception failure) { throw unsupported(); }
                }
            } else if (expression instanceof TrimFunction trim) {
                // 标准 TRIM 使用独立解析节点，两个操作数均须进入字段与子查询授权。
                if (trim.getExpression() == null && trim.getFromExpression() == null) throw unsupported();
                expression(trim.getExpression(), scope, controls, aliases, depth + 1);
                expression(trim.getFromExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof CaseExpression caseExpression) {
                expression(caseExpression.getSwitchExpression(), scope, controls, aliases, depth + 1);
                if (caseExpression.getWhenClauses() != null) for (var when : caseExpression.getWhenClauses()) {
                    expression(when.getWhenExpression(), scope, controls, aliases, depth + 1);
                    expression(when.getThenExpression(), scope, controls, aliases, depth + 1);
                }
                expression(caseExpression.getElseExpression(), scope, controls, aliases, depth + 1);
            } else if (expression instanceof IntervalExpression interval) {
                // JSqlParser 5.2 将 INTERVAL ? DAY 包成两层 IntervalExpression。
                if (interval.getIntervalType() == null && interval.getExpression() instanceof IntervalExpression nested) {
                    expression(nested, scope, controls, aliases, depth + 1);
                    return;
                }
                if (interval.getIntervalType() == null || !Set.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND")
                    .contains(interval.getIntervalType().toUpperCase(Locale.ROOT))) throw unsupported();
                if (interval.getExpression() != null) expression(interval.getExpression(), scope, controls, aliases, depth + 1);
                else if (interval.getParameter() == null || !interval.getParameter().matches("[0-9]+")) throw unsupported();
            } else if (actor.superAdmin() && expression instanceof AllColumns) {
                // 超级管理员在精确租户过滤后保留目录外业务字段查询能力。
            } else throw unsupported();
        }

        private void limit(Expression value) {
            if (value != null && !(value instanceof LongValue) && !(value instanceof JdbcParameter)) throw unsupported();
            if (value instanceof LongValue number && number.getValue() < 0) throw unsupported();
            if (value instanceof JdbcParameter parameter && parameter.isUseFixedIndex()) throw unsupported();
        }

        private final class Scope {
            private final Scope outer;
            private final Set<String> ctes;
            private final Map<String, Binding> bindings = new LinkedHashMap<>();
            private final Set<String> outputAliases = new HashSet<>();
            private Scope(Scope outer, Set<String> ctes) { this.outer = outer; this.ctes = Set.copyOf(ctes); }
            private Binding binding(String alias) {
                var local = bindings.get(normalize(MultiPartName.unquote(alias)));
                if (local != null) return local;
                if (outer != null) return outer.binding(alias);
                throw denied();
            }
            private void aliasColumn(String name, boolean controls) {
                // GROUP/HAVING 对同名别名和物理列的解析优先级随数据库而异，双重解释均须授权。
                bindings.values().stream().filter(binding -> binding.knows(name)).forEach(binding -> binding.use(name, controls));
            }
            private void column(String name, boolean controls) {
                var candidates = bindings.values().stream().filter(binding -> binding.knows(name)).toList();
                if (candidates.size() == 1) candidates.get(0).use(name, controls);
                else if (candidates.isEmpty() && outer != null) outer.column(name, controls);
                else throw denied();
            }
        }

        private final class Binding {
            private final String alias;
            private boolean cte;
            private final String table;
            private final ReportEntityCatalog.Entity entity;
            private final Set<String> derived;
            private final Map<String, ReportEntityCatalog.Field> fields = new LinkedHashMap<>();
            private final Set<String> permissions = new LinkedHashSet<>();
            private final Set<String> usedColumns = new LinkedHashSet<>();
            private Binding(String alias, String table, ReportEntityCatalog.Entity entity, Set<String> derived) {
                this.alias = alias;
                this.table = table;
                this.entity = entity;
                this.derived = derived;
                if (entity != null) {
                    permissions.add(entity.permission());
                    approvedFields(actor, entity).forEach(field -> fields.put(normalize(field.column()), field));
                    if (fields.isEmpty()) throw denied();
                }
            }
            private Set<String> visibleColumns() { return derived == null ? fields.keySet() : derived; }
            private boolean knows(String column) {
                return actor.superAdmin() || visibleColumns().contains(normalize(column)) || entity != null
                    && (entity.fields().stream().anyMatch(field -> field.column().equalsIgnoreCase(column)) || control(column));
            }
            private boolean control(String column) {
                return entity != null && (entity.tenantColumn().equalsIgnoreCase(column)
                    || entity.deleteColumn() != null && entity.deleteColumn().equalsIgnoreCase(column));
            }
            private void use(String column, boolean controls) {
                if (actor.superAdmin()) return;
                if (derived != null) {
                    if (!derived.contains(normalize(column))) throw denied();
                } else {
                    var field = fields.get(normalize(column));
                    if (field == null) { if (!controls || !control(column)) throw denied(); }
                    else {
                        usedColumns.add(normalize(field.column()));
                        if (field.permission() != null) permissions.add(field.permission());
                    }
                }
            }
        }
    }

    private static boolean nonempty(Collection<?> values) { return values != null && !values.isEmpty(); }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT); }
    private static String quote(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) throw unsupported();
        return "`" + identifier + "`";
    }
    private static IllegalArgumentException identityDenied() { return new IllegalArgumentException("业务查询身份无效或授权已撤销"); }
    private static IllegalArgumentException denied() { return new IllegalArgumentException("当前身份无权查询此业务表、字段或数据范围"); }
    private static IllegalArgumentException unsupported() { return new IllegalArgumentException("业务查询包含不安全或尚未支持的结构，请使用受控业务表与字段简化查询"); }
}
