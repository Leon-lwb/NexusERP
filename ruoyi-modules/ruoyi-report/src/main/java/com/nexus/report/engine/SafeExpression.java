package com.nexus.report.engine;

import com.nexus.common.report.ValueType;
import com.nexus.report.domain.ReportFault;

import java.math.*;
import java.util.*;

/** 受限数值表达式语法树，不执行脚本、反射或对象成员访问。 */
public final class SafeExpression {
    /** 类型，负责当前模块对应的契约或处理职责。 */
    private enum Type {
        /** 保存当前 {@code NUMBER} 值。 */
        NUMBER,
        /** 保存当前 {@code BOOLEAN} 值。 */
        BOOLEAN,
        /** 保存当前 {@code NULL} 值。 */
        NULL,
        /** 保存当前 {@code TEXT} 值。 */
        TEXT
    }

    /** 保存当前 {@code root} 值。 */
    private final Node root;

    /** 保存当前 {@code dependencies} 值。 */
    private final Set<String> dependencies;

    /** 保存当前 {@code cost} 值。 */
    private final int cost;

    /**
     * 初始化受限表达式并保存所需依赖或不可变值。
     *
     * @param root 当前操作的 {@code root} 输入
     * @param cost 当前操作的 {@code cost} 输入
     */
    private SafeExpression(Node root, int cost) {
        this.root = root;
        this.cost = cost;
        var names = new TreeSet<String>();
        root.dependencies(names);
        this.dependencies = Set.copyOf(names);
    }

    /**
     * 编译定义并检查字段、类型和业务权限。
     *
     * @param text 当前操作的 {@code text} 输入
     * @return 当前处理返回的固定结果
     */
    public static SafeExpression compile(String text) {
        if (text == null || text.isBlank() || text.length() > 1024)
            throw ReportFault.invalid("公式为空或过长");
        var parser = new Parser(text);
        Node root = parser.expression();
        if (!parser.peek().equals("EOF")) throw ReportFault.invalid("公式包含未支持的语法");
        return new SafeExpression(root, parser.nodes);
    }

    /**
     * 完成 {@code dependencies} 对应的受限表达式处理。
     *
     * @return 当前处理返回的固定结果
     */
    public Set<String> dependencies() {
        return dependencies;
    }

    /**
     * 完成 {@code aggregate} 对应的受限表达式处理。
     *
     * @return 当前检查或条件操作是否成功
     */
    public boolean aggregate() {
        return root.aggregate();
    }

    /**
     * 完成 {@code cost} 对应的受限表达式处理。
     *
     * @return 当前处理返回的固定结果
     */
    public int cost() {
        return cost;
    }

    /**
     * 完成 {@code numericDependencies} 对应的受限表达式处理。
     *
     * @return 当前处理返回的固定结果
     */
    public Set<String> numericDependencies() {
        var result = new TreeSet<String>();
        root.numericDependencies(result);
        return Set.copyOf(result);
    }

    /**
     * 完成 {@code measureDependencies} 对应的受限表达式处理。
     *
     * @return 当前处理返回的固定结果
     */
    public Set<String> measureDependencies() {
        var result = new TreeSet<String>();
        root.measureDependencies(result);
        return Set.copyOf(result);
    }

    /**
     * 完成 {@code session} 对应的受限表达式处理。
     *
     * @param rows 待处理业务行
     * @return 当前处理返回的固定结果
     */
    public Evaluation session(List<Map<String, Object>> rows) {
        return new Evaluation(rows);
    }

    /** Evaluation，负责当前模块对应的契约或处理职责。 */
    public final class Evaluation {
        /** 待处理业务行。 */
        private final List<Map<String, Object>> rows;

        /** 保存当前 {@code cache} 值。 */
        private final Map<Node, Object> cache = new IdentityHashMap<>();

        /**
         * 初始化Evaluation并保存所需依赖或不可变值。
         *
         * @param rows 待处理业务行
         */
        private Evaluation(List<Map<String, Object>> rows) {
            if (aggregate() && rows.size() > 5000) throw ReportFault.capacity();
            this.rows =
                    rows.stream()
                            .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                            .toList();
        }

        /**
         * 完成 {@code evaluate} 对应的Evaluation处理。
         *
         * @param values 绑定值
         * @return 当前处理返回的固定结果
         */
        public Object evaluate(Map<String, Object> values) {
            return root.eval(new Context(values, rows, cache));
        }
    }

    /**
     * 完成 {@code evaluate} 对应的受限表达式处理。
     *
     * @param values 绑定值
     * @param rows 待处理业务行
     * @return 当前处理返回的固定结果
     */
    public Object evaluate(Map<String, Object> values, List<Map<String, Object>> rows) {
        return session(rows).evaluate(values);
    }

    /**
     * 上下文，负责当前模块对应的契约或处理职责。
     *
     * @param values 固定类型的精确值列表
     * @param rows 精确类型的数据行
     * @param cache 当前契约的cache配置或状态
     */
    private record Context(
            Map<String, Object> values, List<Map<String, Object>> rows, Map<Node, Object> cache) {}

    /** Node，负责当前模块对应的契约或处理职责。 */
    private interface Node {
        /**
         * 完成 {@code eval} 对应的Node处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        Object eval(Context context);

        /**
         * 完成 {@code dependencies} 对应的Node处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        default void dependencies(Set<String> names) {}

        /**
         * 完成 {@code aggregate} 对应的Node处理。
         *
         * @return 当前检查或条件操作是否成功
         */
        default boolean aggregate() {
            return false;
        }

        /**
         * 完成 {@code numericDependencies} 对应的Node处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        default void numericDependencies(Set<String> names) {
            dependencies(names);
        }

        /**
         * 完成 {@code measureDependencies} 对应的Node处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        default void measureDependencies(Set<String> names) {}
    }

    /**
     * Literal，负责当前模块对应的契约或处理职责。
     *
     * @param value 当前契约的value配置或状态
     */
    private record Literal(Object value) implements Node {
        /**
         * 完成 {@code eval} 对应的Literal处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        public Object eval(Context context) {
            return value;
        }
    }

    /**
     * Variable，负责当前模块对应的契约或处理职责。
     *
     * @param name 契约名称
     */
    private record Variable(String name) implements Node {
        /**
         * 完成 {@code eval} 对应的Variable处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        public Object eval(Context context) {
            if (!context.values.containsKey(name)) throw ReportFault.invalid("公式字段缺失: " + name);
            return context.values.get(name);
        }

        /**
         * 完成 {@code dependencies} 对应的Variable处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void dependencies(Set<String> names) {
            names.add(name);
        }
    }

    /**
     * Unary，负责当前模块对应的契约或处理职责。
     *
     * @param operator 当前契约的operator配置或状态
     * @param value 当前契约的value配置或状态
     */
    private record Unary(String operator, Node value) implements Node {

        /**
         * 完成 {@code eval} 对应的Unary处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        public Object eval(Context context) {
            Object v = value.eval(context);
            if (v == null) return null;
            return operator.equals("-")
                    ? checked(number(v).negate())
                    : operator.equals("NOT") || operator.equals("!") ? !bool(v) : number(v);
        }

        /**
         * 完成 {@code dependencies} 对应的Unary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void dependencies(Set<String> names) {
            value.dependencies(names);
        }

        /**
         * 完成 {@code aggregate} 对应的Unary处理。
         *
         * @return 当前检查或条件操作是否成功
         */
        public boolean aggregate() {
            return value.aggregate();
        }

        /**
         * 完成 {@code numericDependencies} 对应的Unary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void numericDependencies(Set<String> names) {
            value.numericDependencies(names);
        }

        /**
         * 完成 {@code measureDependencies} 对应的Unary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void measureDependencies(Set<String> names) {
            value.measureDependencies(names);
        }
    }

    /**
     * Binary，负责当前模块对应的契约或处理职责。
     *
     * @param operator 当前契约的operator配置或状态
     * @param left 当前契约的left配置或状态
     * @param right 当前契约的right配置或状态
     */
    private record Binary(String operator, Node left, Node right) implements Node {

        /**
         * 完成 {@code eval} 对应的Binary处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        public Object eval(Context context) {
            Object a = left.eval(context);
            if (operator.equals("AND") || operator.equals("&&")) {
                Boolean x = logical(a);
                if (Boolean.FALSE.equals(x)) return false;
                Boolean y = logical(right.eval(context));
                return Boolean.FALSE.equals(y)
                        ? Boolean.FALSE
                        : x == null || y == null ? null : Boolean.TRUE;
            }
            if (operator.equals("OR") || operator.equals("||")) {
                Boolean x = logical(a);
                if (Boolean.TRUE.equals(x)) return true;
                Boolean y = logical(right.eval(context));
                return Boolean.TRUE.equals(y)
                        ? Boolean.TRUE
                        : x == null || y == null ? null : Boolean.FALSE;
            }
            Object b = right.eval(context);
            if (a == null || b == null) return null;
            if (Set.of("=", "==", "!=").contains(operator)
                    && (a instanceof Boolean || b instanceof Boolean)) {
                boolean equal = bool(a) == bool(b);
                return operator.equals("!=") ? !equal : equal;
            }
            BigDecimal x = number(a), y = number(b);
            return switch (operator) {
                case "+" -> checked(x.add(y));
                case "-" -> checked(x.subtract(y));
                case "*" -> checked(x.multiply(y));
                case "/" -> {
                    if (y.signum() == 0) throw ReportFault.invalid("公式除数为零");
                    yield checked(x.divide(y, 16, RoundingMode.HALF_UP));
                }
                case "%" -> {
                    if (y.signum() == 0) throw ReportFault.invalid("公式除数为零");
                    yield checked(x.remainder(y));
                }
                case "==", "=" -> x.compareTo(y) == 0;
                case "!=" -> x.compareTo(y) != 0;
                case "<" -> x.compareTo(y) < 0;
                case ">" -> x.compareTo(y) > 0;
                case "<=" -> x.compareTo(y) <= 0;
                case ">=" -> x.compareTo(y) >= 0;
                default -> throw ReportFault.invalid("未支持的运算符");
            };
        }

        /**
         * 完成 {@code dependencies} 对应的Binary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void dependencies(Set<String> names) {
            left.dependencies(names);
            right.dependencies(names);
        }

        /**
         * 完成 {@code aggregate} 对应的Binary处理。
         *
         * @return 当前检查或条件操作是否成功
         */
        public boolean aggregate() {
            return left.aggregate() || right.aggregate();
        }

        /**
         * 完成 {@code numericDependencies} 对应的Binary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void numericDependencies(Set<String> names) {
            left.numericDependencies(names);
            right.numericDependencies(names);
        }

        /**
         * 完成 {@code measureDependencies} 对应的Binary处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void measureDependencies(Set<String> names) {
            left.measureDependencies(names);
            right.measureDependencies(names);
        }
    }

    /**
     * Function，负责当前模块对应的契约或处理职责。
     *
     * @param name 契约名称
     * @param arguments 当前契约的arguments配置或状态
     */
    private record Function(String name, List<Node> arguments) implements Node {

        /**
         * 完成 {@code ownAggregate} 对应的Function处理。
         *
         * @return 当前检查或条件操作是否成功
         */
        private boolean ownAggregate() {
            return name.equals("SUM")
                    || name.equals("COUNT")
                    || (name.equals("MIN") || name.equals("MAX")) && arguments.size() == 1;
        }

        /**
         * 完成 {@code eval} 对应的Function处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        public Object eval(Context context) {
            if (name.equals("IF"))
                return arguments
                        .get(Boolean.TRUE.equals(logical(arguments.get(0).eval(context))) ? 1 : 2)
                        .eval(context);
            if (name.equals("COALESCE")) {
                for (Node argument : arguments) {
                    Object value = argument.eval(context);
                    if (value != null) return value;
                }
                return null;
            }
            if (name.equals("ROUND")) {
                Object value = arguments.get(0).eval(context);
                if (value == null) return null;
                int scale;
                try {
                    scale = number(arguments.get(1).eval(context)).intValueExact();
                } catch (ArithmeticException e) {
                    throw ReportFault.invalid("ROUND 精度必须为整数");
                }
                if (scale < 0 || scale > 12) throw ReportFault.invalid("ROUND 精度范围为 0–12");
                return checked(number(value).setScale(scale, RoundingMode.HALF_UP));
            }
            if (ownAggregate()) {
                if (context.cache.containsKey(this)) return context.cache.get(this);
                Object result = aggregateValue(context);
                context.cache.put(this, result);
                return result;
            }
            BigDecimal result = null;
            for (Node argument : arguments) {
                Object value = argument.eval(context);
                if (value != null) {
                    BigDecimal number = number(value);
                    result =
                            result == null
                                    ? number
                                    : name.equals("MIN") ? result.min(number) : result.max(number);
                }
            }
            return result;
        }

        /**
         * 完成 {@code aggregateValue} 对应的Function处理。
         *
         * @param context 服务端执行上下文
         * @return 当前处理返回的固定结果
         */
        private Object aggregateValue(Context context) {
            if (name.equals("COUNT") && arguments.isEmpty())
                return BigDecimal.valueOf(context.rows.size());
            long count = 0;
            BigDecimal result = null;
            for (Map<String, Object> row : context.rows) {
                Object value = arguments.get(0).eval(new Context(row, context.rows, context.cache));
                if (value == null) continue;
                if (name.equals("COUNT")) {
                    count++;
                    continue;
                }
                BigDecimal number = number(value);
                result =
                        result == null
                                ? number
                                : name.equals("SUM")
                                        ? checked(result.add(number))
                                        : name.equals("MIN")
                                                ? result.min(number)
                                                : result.max(number);
            }
            return name.equals("COUNT") ? BigDecimal.valueOf(count) : result;
        }

        /**
         * 完成 {@code dependencies} 对应的Function处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void dependencies(Set<String> names) {
            arguments.forEach(node -> node.dependencies(names));
        }

        /**
         * 完成 {@code numericDependencies} 对应的Function处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void numericDependencies(Set<String> names) {
            if (name.equals("COUNT")
                    && (arguments.isEmpty() || arguments.get(0) instanceof Variable)) return;
            arguments.forEach(node -> node.numericDependencies(names));
        }

        /**
         * 完成 {@code measureDependencies} 对应的Function处理。
         *
         * @param names 当前操作的 {@code names} 输入
         */
        public void measureDependencies(Set<String> names) {
            if (name.equals("SUM")
                    || (name.equals("MIN") || name.equals("MAX")) && arguments.size() == 1)
                arguments.forEach(node -> node.dependencies(names));
            arguments.forEach(node -> node.measureDependencies(names));
        }

        /**
         * 完成 {@code aggregate} 对应的Function处理。
         *
         * @return 当前检查或条件操作是否成功
         */
        public boolean aggregate() {
            return name.equals("SUM")
                    || name.equals("COUNT")
                    || (name.equals("MIN") || name.equals("MAX")) && arguments.size() == 1
                    || arguments.stream().anyMatch(Node::aggregate);
        }
    }

    /**
     * 完成 {@code number} 对应的受限表达式处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal decimal) return checked(decimal);
        if (value instanceof Number number) {
            try {
                return checked(new BigDecimal(number.toString()));
            } catch (NumberFormatException e) {
                throw ReportFault.invalid("公式数值无效");
            }
        }
        throw ReportFault.invalid("公式仅支持数值字段");
    }

    /**
     * 完成 {@code logical} 对应的受限表达式处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    private static Boolean logical(Object value) {
        return value == null ? null : bool(value);
    }

    /**
     * 完成 {@code bool} 对应的受限表达式处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前检查或条件操作是否成功
     */
    private static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        throw ReportFault.invalid("逻辑运算需要比较结果或布尔值");
    }

    /**
     * 完成 {@code checked} 对应的受限表达式处理。
     *
     * @param value 当前操作的 {@code value} 输入
     * @return 当前处理返回的固定结果
     */
    static BigDecimal checked(BigDecimal value) {
        if (value.precision() > 38
                || (long) value.precision() - Math.min(value.scale(), 0) > 38
                || value.scale() < -64
                || value.scale() > 64) throw ReportFault.invalid("公式数值超出精度范围");
        return value;
    }

    /**
     * 完成 {@code aggregateDependencies} 对应的受限表达式处理。
     *
     * @return 当前处理返回的固定结果
     */
    Set<String> aggregateDependencies() {
        var names = new TreeSet<String>();
        aggregateDependencies(root, names);
        return Set.copyOf(names);
    }

    /**
     * 完成 {@code aggregateDependencies} 对应的受限表达式处理。
     *
     * @param node 当前操作的 {@code node} 输入
     * @param names 当前操作的 {@code names} 输入
     */
    private static void aggregateDependencies(Node node, Set<String> names) {
        if (node instanceof Function function) {
            if (function.ownAggregate()) function.dependencies(names);
            function.arguments.forEach(argument -> aggregateDependencies(argument, names));
        } else if (node instanceof Unary unary) aggregateDependencies(unary.value, names);
        else if (node instanceof Binary binary) {
            aggregateDependencies(binary.left, names);
            aggregateDependencies(binary.right, names);
        }
    }

    /**
     * 校验Types的受限结构及输入边界。
     *
     * @param fields 允许访问的字段
     * @return 当前处理返回的固定结果
     */
    ValueType validateTypes(Map<String, ValueType> fields) {
        Type result = type(root, fields);
        if (result == Type.TEXT) throw ReportFault.invalid("计算列仅支持数值或布尔结果");
        return result == Type.BOOLEAN ? ValueType.BOOLEAN : ValueType.DECIMAL;
    }

    /**
     * 完成 {@code type} 对应的受限表达式处理。
     *
     * @param node 当前操作的 {@code node} 输入
     * @param fields 允许访问的字段
     * @return 当前处理返回的固定结果
     */
    private static Type type(Node node, Map<String, ValueType> fields) {
        if (node instanceof Literal literal)
            return literal.value == null
                    ? Type.NULL
                    : literal.value instanceof Boolean ? Type.BOOLEAN : Type.NUMBER;
        if (node instanceof Variable variable) {
            ValueType value = fields.get(variable.name);
            if (value == null) throw ReportFault.invalid("公式字段类型缺失");
            return value == ValueType.DECIMAL || value == ValueType.INTEGER
                    ? Type.NUMBER
                    : value == ValueType.BOOLEAN ? Type.BOOLEAN : Type.TEXT;
        }
        if (node instanceof Unary unary) {
            boolean logical = Set.of("NOT", "!").contains(unary.operator);
            requireType(type(unary.value, fields), logical ? Type.BOOLEAN : Type.NUMBER);
            return logical ? Type.BOOLEAN : Type.NUMBER;
        }
        if (node instanceof Binary binary) {
            Type left = type(binary.left, fields), right = type(binary.right, fields);
            if (Set.of("AND", "&&", "OR", "||").contains(binary.operator)) {
                requireType(left, Type.BOOLEAN);
                requireType(right, Type.BOOLEAN);
                return Type.BOOLEAN;
            }
            if (Set.of("=", "==", "!=").contains(binary.operator)) {
                compatible(left, right);
                if (left == Type.TEXT || right == Type.TEXT)
                    throw ReportFault.invalid("比较仅支持数值或布尔值");
                return Type.BOOLEAN;
            }
            requireType(left, Type.NUMBER);
            requireType(right, Type.NUMBER);
            return Set.of("<", ">", "<=", ">=").contains(binary.operator)
                    ? Type.BOOLEAN
                    : Type.NUMBER;
        }
        Function function = (Function) node;
        if (function.name.equals("COUNT")) {
            function.arguments.forEach(argument -> type(argument, fields));
            return Type.NUMBER;
        }
        if (function.name.equals("IF")) {
            requireType(type(function.arguments.get(0), fields), Type.BOOLEAN);
            Type result =
                    compatible(
                            type(function.arguments.get(1), fields),
                            type(function.arguments.get(2), fields));
            if (result == Type.TEXT) throw ReportFault.invalid("条件分支仅支持数值或布尔值");
            return result;
        }
        if (function.name.equals("COALESCE")) {
            Type result = Type.NULL;
            for (Node argument : function.arguments)
                result = compatible(result, type(argument, fields));
            if (result == Type.TEXT) throw ReportFault.invalid("空值替代仅支持数值或布尔值");
            return result;
        }
        for (Node argument : function.arguments) requireType(type(argument, fields), Type.NUMBER);
        return Type.NUMBER;
    }

    /**
     * 完成 {@code requireType} 对应的受限表达式处理。
     *
     * @param actual 当前操作的 {@code actual} 输入
     * @param expected 预期版本或状态
     */
    private static void requireType(Type actual, Type expected) {
        if (actual != Type.NULL && actual != expected) throw ReportFault.invalid("公式操作数类型不匹配");
    }

    /**
     * 完成 {@code compatible} 对应的受限表达式处理。
     *
     * @param left 当前操作的 {@code left} 输入
     * @param right 当前操作的 {@code right} 输入
     * @return 当前处理返回的固定结果
     */
    private static Type compatible(Type left, Type right) {
        if (left == Type.NULL) return right;
        if (right == Type.NULL) return left;
        if (left != right) throw ReportFault.invalid("公式分支或比较类型不匹配");
        return left;
    }

    /** Parser，负责当前模块对应的契约或处理职责。 */
    private static final class Parser {
        /** 保存当前 {@code tokens} 值。 */
        private final List<String> tokens = new ArrayList<>();

        /** 保存当前 {@code position} 值。 */
        private int position;

        /** 保存当前 {@code depth} 值。 */
        private int depth;

        /** 保存当前 {@code nodes} 值。 */
        private int nodes;

        /**
         * 初始化Parser并保存所需依赖或不可变值。
         *
         * @param input 待校验输入
         */
        Parser(String input) {
            int i = 0;
            while (i < input.length()) {
                char c = input.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (Character.isDigit(c)
                        || c == '.'
                                && i + 1 < input.length()
                                && Character.isDigit(input.charAt(i + 1))) {
                    int start = i++;
                    while (i < input.length()
                            && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) i++;
                    String number = input.substring(start, i);
                    if (!number.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
                        throw ReportFault.invalid("非法数字");
                    tokens.add(number);
                } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_') {
                    int start = i++;
                    while (i < input.length()
                            && (Character.isLetterOrDigit(input.charAt(i))
                                    || input.charAt(i) == '_')) i++;
                    String word = input.substring(start, i);
                    if (word.length() > 64) throw ReportFault.invalid("公式字段名过长");
                    tokens.add(word);
                } else {
                    String two = i + 1 < input.length() ? input.substring(i, i + 2) : "";
                    if (Set.of("<=", ">=", "==", "!=", "&&", "||").contains(two)) {
                        tokens.add(two);
                        i += 2;
                    } else if ("+-*/%()!,<>=,".indexOf(c) >= 0) {
                        tokens.add(String.valueOf(c));
                        i++;
                    } else throw ReportFault.invalid("公式包含禁止的字符");
                }
                if (tokens.size() > 256) throw ReportFault.invalid("公式过于复杂");
            }
            tokens.add("EOF");
        }

        /**
         * 完成 {@code peek} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        String peek() {
            return position < tokens.size() ? tokens.get(position) : "EOF";
        }

        /**
         * 判断当前输入是否匹配受限语法。
         *
         * @param values 绑定值
         * @return 当前检查或条件操作是否成功
         */
        boolean match(String... values) {
            for (String v : values)
                if (peek().equals(v)) {
                    position++;
                    return true;
                }
            return false;
        }

        /**
         * 完成 {@code expect} 对应的Parser处理。
         *
         * @param value 当前操作的 {@code value} 输入
         */
        void expect(String value) {
            if (!match(value)) throw ReportFault.invalid("公式括号或参数不完整");
        }

        /**
         * 完成 {@code count} 对应的Parser处理。
         *
         * @param node 当前操作的 {@code node} 输入
         * @return 当前处理返回的固定结果
         */
        Node count(Node node) {
            if (++nodes > 256) throw ReportFault.invalid("公式过于复杂");
            return node;
        }

        /**
         * 完成 {@code expression} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node expression() {
            return or();
        }

        /**
         * 完成 {@code or} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node or() {
            Node n = and();
            while (match("OR", "||")) n = count(new Binary(tokens.get(position - 1), n, and()));
            return n;
        }

        /**
         * 完成 {@code and} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node and() {
            Node n = comparison();
            while (match("AND", "&&"))
                n = count(new Binary(tokens.get(position - 1), n, comparison()));
            return n;
        }

        /**
         * 完成 {@code comparison} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node comparison() {
            Node n = addition();
            if (match("=", "==", "!=", "<", ">", "<=", ">="))
                n = count(new Binary(tokens.get(position - 1), n, addition()));
            return n;
        }

        /**
         * 完成 {@code addition} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node addition() {
            Node n = multiply();
            while (match("+", "-")) n = count(new Binary(tokens.get(position - 1), n, multiply()));
            return n;
        }

        /**
         * 完成 {@code multiply} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node multiply() {
            Node n = unary();
            while (match("*", "/", "%"))
                n = count(new Binary(tokens.get(position - 1), n, unary()));
            return n;
        }

        /**
         * 完成 {@code unary} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node unary() {
            if (++depth > 32) throw ReportFault.invalid("公式嵌套过深");
            try {
                if (match("+", "-", "!", "NOT"))
                    return count(new Unary(tokens.get(position - 1), unary()));
                return primary();
            } finally {
                depth--;
            }
        }

        /**
         * 完成 {@code primary} 对应的Parser处理。
         *
         * @return 当前处理返回的固定结果
         */
        Node primary() {
            if (match("(")) {
                Node n = expression();
                expect(")");
                return n;
            }
            String word = peek();
            if (word.equals("EOF")) throw ReportFault.invalid("缺少公式值");
            position++;
            if (word.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
                return count(new Literal(checked(new BigDecimal(word))));
            if (word.equals("NULL")) return count(new Literal(null));
            if (word.equals("TRUE")) return count(new Literal(true));
            if (word.equals("FALSE")) return count(new Literal(false));
            if (!word.matches("[A-Za-z_][A-Za-z0-9_]*")) throw ReportFault.invalid("缺少公式值");
            if (!match("(")) return count(new Variable(word));
            String name = word.toUpperCase(Locale.ROOT);
            if (!Set.of("IF", "COALESCE", "ROUND", "SUM", "COUNT", "MIN", "MAX").contains(name))
                throw ReportFault.invalid("未支持的公式函数");
            List<Node> arguments = new ArrayList<>();
            if (!match(")")) {
                do {
                    if (arguments.size() >= 8) throw ReportFault.invalid("函数参数过多");
                    arguments.add(expression());
                } while (match(","));
                expect(")");
            }
            int size = arguments.size();
            boolean valid =
                    switch (name) {
                        case "IF" -> size == 3;
                        case "ROUND" -> size == 2;
                        case "SUM" -> size == 1;
                        case "COUNT" -> size <= 1;
                        case "COALESCE" -> size >= 2;
                        default -> size >= 1;
                    };
            if (!valid) throw ReportFault.invalid("函数参数数量不正确");
            boolean ownAggregate =
                    name.equals("SUM")
                            || name.equals("COUNT")
                            || (name.equals("MIN") || name.equals("MAX")) && size == 1;
            if (ownAggregate && arguments.stream().anyMatch(Node::aggregate))
                throw ReportFault.invalid("不支持嵌套聚合函数");
            return count(new Function(name, List.copyOf(arguments)));
        }
    }
}
