package com.nexus.ai.service.query;

import com.baomidou.mybatisplus.extension.plugins.inner.BaseMultiTableInnerInterceptor;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.MultiPartName;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 只读安全校验器（公共，ExecuteSqlQueryTool 与 flow 的 sql 节点复用）
 * <p>安全链路：SELECT 白名单 + 注释清洗 + 危险关键词黑名单 + AST 物理表前缀白名单+ 租户隔离注入 + LIMIT 强加 + EXPLAIN 语法预校验</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlSafetyValidator {

    /** ERP 查询服务（只读 SQL/EXPLAIN，@DS("erp") 数据源）；表存在性不是授权依据。 */
    private final ErpQueryService erpQueryService;
    /** AI 配置属性（表前缀白名单 + 行数/超时限制） */
    private final AiProperties aiProperties;

    /** 禁止出现的 SQL 关键词正则（单词边界避免子串误匹配，如 UPDATE_TIME 不会命中 UPDATE） */
    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
        "\\b(INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD_FILE|LOAD\\s+DATA|" +
        "INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|" +
        "GRANT|REVOKE|EXECUTE|INFORMATION_SCHEMA|" +
        "SLEEP|BENCHMARK|WAITFOR)\\b",
        Pattern.CASE_INSENSITIVE);

    /** 禁止 UNION（含 UNION ALL：多分支 SELECT 时租户条件只注入第一分支，第二分支零隔离 → 跨租户读取） */
    private static final Pattern FORBIDDEN_UNION = Pattern.compile(
        "\\bUNION\\b", Pattern.CASE_INSENSITIVE);

    /** 租户字面量的窄字符白名单：拒绝引号、反斜杠、空白及 SQL/正则替换元字符。 */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]+");

    /** 当前仅支持简单物理表标识符；授权只看显式配置的非空前缀。 */
    private static final Pattern SIMPLE_TABLE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    /**
     * 校验并净化 SQL（只读 SELECT）
     * <p>通过则返回清洗注释 + 注入租户条件 + 追加行数上限后的可执行 SQL；
     * 不通过抛 {@link IllegalArgumentException}（错误信息中文可读）</p>
     *
     * @param sql     原始 SQL
     * @param maxRows 行数上限（null 时取配置 nexus.ai.sql-max-rows）
     * @return 可安全执行的 SQL
     */
    public String validateAndSanitize(String sql, Integer maxRows) {
        // 无显式租户（AI 对话链路）：从 AiRequestContext 获取（对话线程 ThreadLocal 可达）
        return validateAndSanitize(sql, maxRows, null);
    }

    /**
     * SQL 安全校验（带显式租户）
     * <p>流程 sql 节点在 ForkJoinPool 线程执行（AiRequestContext ThreadLocal 不可达），
     * 必须由调用方经执行上下文传入租户，否则租户隔离注入失效（跨租户数据泄露风险）</p>
     *
     * @param sql     原始 SQL
     * @param maxRows 行数上限（null 时取配置 nexus.ai.sql-max-rows）
     * @param tenantId 精确字符串租户（null 时回退 AiRequestContext；缺失或格式非法则拒绝）
     * @return 可安全执行的 SQL
     */
    public String validateAndSanitize(String sql, Integer maxRows, String tenantId) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        // 0. 移除 SQL 注释（防止通过注释绕过关键词检测）
        // 块注释开启 DOTALL：跨行 /* \n TENANT_ID \n */ 也必须被清洗，否则藏匿的关键词
        // 会让后续租户注入判断失效
        String cleanedSql = sql.replaceAll("(?s)/\\*.*?\\*/", " ");  // 块注释 /*...*/（含跨行）
        cleanedSql = cleanedSql.replaceAll("--[^\\n]*", " ");      // 单行注释 --
        cleanedSql = cleanedSql.replaceAll("#[^\\n]*", " ");      // 单行注释 #
        String cleanedUpperSql = cleanedSql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!cleanedUpperSql.startsWith("SELECT")) {
            throw new IllegalArgumentException("仅允许 SELECT 查询语句");
        }

        // 2. 禁止危险关键词（单词边界匹配，避免列名 UPDATE_TIME 误触发 UPDATE）
        Matcher forbiddenMatcher = FORBIDDEN_KEYWORD_PATTERN.matcher(cleanedUpperSql);
        if (forbiddenMatcher.find()) {
            String matched = cleanedUpperSql.substring(
                Math.max(0, forbiddenMatcher.start() - 10),
                Math.min(cleanedUpperSql.length(), forbiddenMatcher.end() + 10)).trim();
            log.warn("[AI-SQL] 包含禁止关键词: {}", matched);
            throw new IllegalArgumentException("SQL 包含禁止关键词: " + forbiddenMatcher.group());
        }
        // 禁止 UNION（含 UNION ALL，多分支零租户隔离风险）
        if (FORBIDDEN_UNION.matcher(cleanedUpperSql).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的 UNION，请拆分为多条简单查询");
        }

        // 3. 显式前缀配置必须完整有效；后续 AST 访问器检查每个物理表，不能扫描原文猜表名。
        List<String> allowedPrefixes = requireAllowedTablePrefixes();

        // 4. 注入租户隔离条件（显式租户优先，回退 AiRequestContext）
        // 安全：先重写用户书写的 tenant_id 等值条件为服务端租户（防伪造租户值），
        // 再无条件追加服务端条件兜底
        String effectiveTenantId = tenantId;
        if (effectiveTenantId == null) {
            var contextTenantId = AiRequestContext.getTenantId();
            effectiveTenantId = contextTenantId == null ? null : contextTenantId.toString();
        }
        if (effectiveTenantId == null || !TENANT_ID_PATTERN.matcher(effectiveTenantId).matches()) {
            throw new IllegalArgumentException("租户ID缺失或格式非法，禁止无租户或不安全的 SQL 查询");
        }
        // 仅对白名单校验通过的身份构造字符串字面量；不转数字、不补零、不裁剪。
        String tenantLiteral = "'" + effectiveTenantId + "'";
        cleanedSql = rewriteTenantValues(cleanedSql, tenantLiteral);
        cleanedSql = injectTenantCondition(cleanedSql, effectiveTenantId, allowedPrefixes);

        // 5. 追加行数限制（移除已有的 LIMIT，强制使用安全限制）
        cleanedSql = cleanedSql.replaceAll("(?i)\\s+LIMIT\\s+\\d+(?:\\s*,\\s*\\d+)?\\s*;?\\s*$", "");
        int limit = maxRows == null || maxRows <= 0 ? aiProperties.getSqlMaxRows() : maxRows;
        cleanedSql = cleanedSql + " LIMIT " + limit;

        // 6. SQL 语法预校验（通过 EXPLAIN 验证，避免执行语法错误的 SQL）
        String explainError = validateSqlSyntax(cleanedSql);
        if (explainError != null) {
            throw new IllegalArgumentException("SQL 语法错误：" + explainError);
        }
        return cleanedSql;
    }

    /** 配置为空或含空/非法前缀时整体拒绝，禁止空串前缀意外授权所有表。 */
    private List<String> requireAllowedTablePrefixes() {
        List<String> prefixes = aiProperties.getAllowedTablePrefixes();
        if (prefixes == null || prefixes.isEmpty()
            || prefixes.stream().anyMatch(prefix -> prefix == null
                || !SIMPLE_TABLE_IDENTIFIER.matcher(prefix).matches())) {
            throw new IllegalArgumentException("表名前缀白名单未配置或格式非法，禁止查询");
        }
        // 使用当前请求的不可变快照；大小写只影响授权匹配，不改写实际 SQL 表标识符。
        return prefixes.stream().map(prefix -> prefix.toLowerCase(Locale.ROOT)).toList();
    }

    /** 只做显式前缀匹配；数据库中存在的表绝不能自动取得查询权限。 */
    private boolean isTableAllowed(String tableName, List<String> prefixes) {
        String normalized = tableName.toLowerCase(Locale.ROOT);
        return prefixes.stream().anyMatch(normalized::startsWith);
    }

    /** 检查真实物理表，而不是列限定符、派生表别名或字符串常量中的伪 FROM/JOIN。 */
    private void validatePhysicalTable(Table table, List<String> prefixes) {
        if (table.getNameParts().size() != 1) {
            throw new IllegalArgumentException("暂不支持限定 schema/catalog 的表名，请使用当前 ERP 数据源的简单表名");
        }
        String tableName = MultiPartName.unquote(table.getNameParts().get(0));
        if (tableName == null || !SIMPLE_TABLE_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("暂不支持特殊或含限定符的物理表名");
        }
        if (!isTableAllowed(tableName, prefixes)) {
            throw new IllegalArgumentException("表 '" + tableName + "' 不在允许的前缀白名单中");
        }
    }

    /**
     * 用户书写的 tenant_id 等值条件（含别名前缀）：{@code [别名.]tenant_id = '值'|数字}。
     * <p>用于把用户/LLM 提供的租户值强制重写为服务端租户值（结构保持不变）。
     * 别名前缀可选：裸 {@code tenant_id = 'x'} 与 {@code t.tenant_id = 'x'} 均命中；
     * 列引用（SELECT tenant_id）无 = 条件不命中。</p>
     */
    private static final Pattern USER_TENANT_CONDITION = Pattern.compile(
        "(?i)((?:[a-zA-Z_][a-zA-Z0-9_]*\\.)?tenant_id\\s*=\\s*)('[^']*'|[0-9]+)");

    /**
     * 注入租户隔离条件。
     * <p>使用 MyBatis-Plus 同版本 JSqlParser 的多表处理器，而不是字符串拼接：
     * 对支持的 SELECT/JOIN/子查询按 AST 追加条件；未证明安全的 CASE/HAVING/ON 子查询、
     * 内嵌 WITH 和括号 JOIN 先拒绝，不能以语法解析成功代替租户隔离证明。</p>
     *
     * @param sql SQL（已清洗注释并重写用户租户条件）
     * @param tenantId 已通过窄字符白名单校验的原始租户文本
     * @return 追加所有可见表租户条件的 SQL
     */
    private String injectTenantCondition(String sql, String tenantId, List<String> allowedPrefixes) {
        return new TenantIsolationParser(tenantId, table -> validatePhysicalTable(table, allowedPrefixes)).append(sql);
    }

    /**
     * 复用项目实际依赖的 JSqlParser 版本，为每张业务表构建精确文本租户条件。
     * 每次调用创建实例，避免请求之间共享租户值。
     */
    private static final class TenantIsolationParser extends BaseMultiTableInnerInterceptor {
        private final String tenantId;
        private final Consumer<Table> tableValidator;

        private TenantIsolationParser(String tenantId, Consumer<Table> tableValidator) {
            this.tenantId = tenantId;
            this.tableValidator = tableValidator;
        }

        private String append(String sql) {
            return parserSingle(sql, null);
        }

        @Override
        protected void processSelect(net.sf.jsqlparser.statement.select.Select select,
                                     int index, String sql, Object obj) {
            // 先检查完整查询树的未支持结构，确保在租户改写及后续 EXPLAIN 前 fail-closed。
            try {
                new UnsupportedStructureGuard(tableValidator).getTables((Statement) select);
            } catch (UnsupportedOperationException e) {
                throw new IllegalArgumentException("SQL 包含不支持的结构，请简化查询", e);
            }
            // JsqlParserSupport 的默认实现只为具体拦截器留扩展点；这里复用多表处理器的
            // SelectBody 遍历，确保顶层、JOIN 和嵌套查询全部进入 buildTableExpression。
            processSelectBody(select, sql);
        }

        @Override
        protected void processPlainSelect(PlainSelect select, String whereSegment) {
            super.processPlainSelect(select, whereSegment);
            // MyBatis-Plus 对普通/外连接已把右表条件放到适当的 ON/WHERE 位置。
            // CROSS JOIN 没有 ON，且部分版本不会把右表交给多表列表；只对 CROSS JOIN
            // 在当前 WHERE 追加右表条件。判断基于AST节点，不扫描原文，避免字符串常量
            // "m.tenant_id" 冒充真正的租户列。
            Expression where = select.getWhere();
            if (select.getJoins() != null) {
                for (Join join : select.getJoins()) {
                    if (join.isCross() && join.getRightItem() instanceof Table table) {
                        Expression tenantCondition = buildTableExpression(table, where, whereSegment);
                        // INNER JOIN可能把主表租户条件放在ON中，当前WHERE仍是裸OR；追加AND前必须保留括号。
                        Expression protectedWhere = where instanceof OrExpression
                            ? new ParenthesedExpressionList<>(where) : where;
                        where = protectedWhere == null ? tenantCondition
                            : new AndExpression(protectedWhere, tenantCondition);
                    }
                }
            }
            select.setWhere(where);
        }

        @Override
        public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
            String tableName = table.getAlias() != null && table.getAlias().getName() != null
                ? table.getAlias().getName() : table.getName();
            Column tenantColumn = new Column(tableName + ".tenant_id");
            return new EqualsTo(tenantColumn, new StringValue(tenantId));
        }
    }

    /**
     * 校验物理表白名单并拒绝当前多表拦截器未完整处理的结构，不重新实现 SQL 租户注入框架。
     * <p>复用 JSqlParser 的树访问器定位节点，避免字符串常量、大小写或嵌套包装绕过。</p>
     */
    private static final class UnsupportedStructureGuard extends TablesNamesFinder<Void> {
        private final Consumer<Table> tableValidator;

        private UnsupportedStructureGuard(Consumer<Table> tableValidator) {
            this.tableValidator = tableValidator;
        }

        @Override
        public <S> Void visit(Table table, S context) {
            // 每个物理 Table 节点独立授权，包含逗号 JOIN、JOIN 右表与所有已支持的嵌套 SELECT。
            tableValidator.accept(table);
            return null;
        }

        @Override
        public <S> Void visit(WithItem<?> item, S context) {
            throw new IllegalArgumentException("暂不支持 WITH/CTE 查询，请拆分为简单 SELECT");
        }

        @Override
        public <S> Void visit(ParenthesedFromItem item, S context) {
            // 括号 FROM/JOIN 分组与普通 (SELECT ...) 派生表是不同 AST 节点。
            throw new IllegalArgumentException("暂不支持括号 FROM/JOIN 分组，请使用普通 JOIN");
        }

        @Override
        public <S> Void visit(CaseExpression expression, S context) {
            if (containsSubquery(expression)) {
                throw new IllegalArgumentException("暂不支持含子查询的 CASE 表达式");
            }
            return super.visit(expression, context);
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            if (containsSubquery(select.getHaving())) {
                throw new IllegalArgumentException("暂不支持含子查询的 HAVING 条件");
            }
            if (select.getJoins() != null) {
                for (Join join : select.getJoins()) {
                    for (Expression on : join.getOnExpressions()) {
                        if (containsSubquery(on)) {
                            throw new IllegalArgumentException("暂不支持 JOIN ON 条件中的子查询");
                        }
                    }
                }
            }
            super.visit(select, context);
            // TablesNamesFinder 默认不遍历 ORDER/GROUP 表达式，在此补查其中的嵌套禁用节点。
            if (select.getOrderByElements() != null) {
                select.getOrderByElements().forEach(order -> order.getExpression().accept(this, context));
            }
            if (select.getGroupBy() != null && select.getGroupBy().getGroupByExpressionList() != null) {
                select.getGroupBy().getGroupByExpressionList().accept(this, context);
            }
            return null;
        }

        private static boolean containsSubquery(Expression expression) {
            if (expression == null) {
                return false;
            }
            boolean[] found = {false};
            expression.accept(new ExpressionVisitorAdapter<Void>() {
                @Override
                public <S> Void visit(Select select, S context) {
                    found[0] = true;
                    return null;
                }
            }, null);
            return found[0];
        }
    }

    /**
     * 把 SQL 中用户书写的 tenant_id 等值条件的值强制重写为服务端租户值。
     * <p>不剥离条件结构（避免破坏 WHERE ... AND ... 语法）：{@code WHERE tenant_id='999999'}
     * → {@code WHERE tenant_id=<服务端租户>}。等值条件被重写后，再由
     * {@link #injectTenantCondition} 无条件追加服务端条件兜底（拦截 !=/IN 等绕过变体）。</p>
     */
    private String rewriteTenantValues(String sql, String tenantLiteral) {
        return USER_TENANT_CONDITION.matcher(sql).replaceAll("$1" + Matcher.quoteReplacement(tenantLiteral));
    }

    /**
     * SQL 语法预校验（EXPLAIN）
     *
     * @param sql 待校验 SQL
     * @return 错误信息，null 表示通过
     */
    private String validateSqlSyntax(String sql) {
        try {
            erpQueryService.executeQuery("EXPLAIN " + sql, 1, 2);
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            return msg;
        }
    }
}
