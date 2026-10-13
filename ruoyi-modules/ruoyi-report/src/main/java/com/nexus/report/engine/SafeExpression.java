package com.nexus.report.engine;
import com.nexus.report.domain.ReportFault;
import com.nexus.common.report.ValueType;
import java.math.*;
import java.util.*;
/** A small numeric AST, never JavaScript/SpEL/Groovy. No reflection or object access. */
public final class SafeExpression {
    private enum Type { NUMBER, BOOLEAN, NULL, TEXT }
    private final Node root;
    private final Set<String> dependencies;
    private final int cost;
    private SafeExpression(Node root, int cost) {
        this.root=root; this.cost=cost; var names=new TreeSet<String>(); root.dependencies(names); this.dependencies=Set.copyOf(names);
    }
    public static SafeExpression compile(String text) {
        if(text==null || text.isBlank() || text.length()>1024) throw ReportFault.invalid("公式为空或过长");
        var parser=new Parser(text); Node root=parser.expression();
        if(!parser.peek().equals("EOF")) throw ReportFault.invalid("公式包含未支持的语法");
        return new SafeExpression(root,parser.nodes);
    }
    public Set<String> dependencies() { return dependencies; }
    public boolean aggregate() { return root.aggregate(); }
    public int cost() { return cost; }
    public Set<String> numericDependencies(){var result=new TreeSet<String>();root.numericDependencies(result);return Set.copyOf(result);}
    public Set<String> measureDependencies(){var result=new TreeSet<String>();root.measureDependencies(result);return Set.copyOf(result);}
    public Evaluation session(List<Map<String,Object>> rows){return new Evaluation(rows);}
    public final class Evaluation {
        private final List<Map<String,Object>> rows;
        private final Map<Node,Object> cache=new IdentityHashMap<>();
        private Evaluation(List<Map<String,Object>> rows){
            if(aggregate()&&rows.size()>5000)throw ReportFault.capacity();
            this.rows=rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
        }
        public Object evaluate(Map<String,Object> values){return root.eval(new Context(values,rows,cache));}
    }
    public Object evaluate(Map<String,Object> values,List<Map<String,Object>> rows) { return session(rows).evaluate(values); }
    private record Context(Map<String,Object> values,List<Map<String,Object>> rows,Map<Node,Object> cache) { }
    private interface Node {
        Object eval(Context context);
        default void dependencies(Set<String> names) { }
        default boolean aggregate() { return false; }
        default void numericDependencies(Set<String> names){dependencies(names);}
        default void measureDependencies(Set<String> names){ }
    }
    private record Literal(Object value) implements Node { public Object eval(Context context) { return value; } }
    private record Variable(String name) implements Node {
        public Object eval(Context context) {
            if(!context.values.containsKey(name)) throw ReportFault.invalid("公式字段缺失: "+name);
            return context.values.get(name);
        }
        public void dependencies(Set<String> names) { names.add(name); }
    }
    private record Unary(String operator,Node value) implements Node {
        public Object eval(Context context) {
            Object v=value.eval(context); if(v==null) return null;
            return operator.equals("-")? checked(number(v).negate()):operator.equals("NOT")||operator.equals("!")?!bool(v):number(v);
        }
        public void dependencies(Set<String> names) { value.dependencies(names); }
        public boolean aggregate() { return value.aggregate(); }
        public void numericDependencies(Set<String> names){value.numericDependencies(names);}
        public void measureDependencies(Set<String> names){value.measureDependencies(names);}
    }
    private record Binary(String operator,Node left,Node right) implements Node {
        public Object eval(Context context) {
            Object a=left.eval(context);
            if(operator.equals("AND")||operator.equals("&&")) {
                Boolean x=logical(a);if(Boolean.FALSE.equals(x))return false;
                Boolean y=logical(right.eval(context));return Boolean.FALSE.equals(y)?Boolean.FALSE:x==null||y==null?null:Boolean.TRUE;
            }
            if(operator.equals("OR")||operator.equals("||")) {
                Boolean x=logical(a);if(Boolean.TRUE.equals(x))return true;
                Boolean y=logical(right.eval(context));return Boolean.TRUE.equals(y)?Boolean.TRUE:x==null||y==null?null:Boolean.FALSE;
            }
            Object b=right.eval(context); if(a==null||b==null) return null;
            if(Set.of("=","==","!=").contains(operator)&&(a instanceof Boolean||b instanceof Boolean)){
                boolean equal=bool(a)==bool(b);return operator.equals("!=")?!equal:equal;
            }
            BigDecimal x=number(a),y=number(b);
            return switch(operator) {
                case "+" -> checked(x.add(y)); case "-" -> checked(x.subtract(y)); case "*" -> checked(x.multiply(y));
                case "/" -> { if(y.signum()==0) throw ReportFault.invalid("公式除数为零"); yield checked(x.divide(y,16,RoundingMode.HALF_UP)); }
                case "%" -> { if(y.signum()==0) throw ReportFault.invalid("公式除数为零"); yield checked(x.remainder(y)); }
                case "==","=" -> x.compareTo(y)==0; case "!=" -> x.compareTo(y)!=0;
                case "<" -> x.compareTo(y)<0; case ">" -> x.compareTo(y)>0; case "<=" -> x.compareTo(y)<=0; case ">=" -> x.compareTo(y)>=0;
                default -> throw ReportFault.invalid("未支持的运算符");
            };
        }
        public void dependencies(Set<String> names) { left.dependencies(names);right.dependencies(names); }
        public boolean aggregate() { return left.aggregate()||right.aggregate(); }
        public void numericDependencies(Set<String> names){left.numericDependencies(names);right.numericDependencies(names);}
        public void measureDependencies(Set<String> names){left.measureDependencies(names);right.measureDependencies(names);}
    }
    private record Function(String name,List<Node> arguments) implements Node {
        private boolean ownAggregate(){return name.equals("SUM")||name.equals("COUNT")||(name.equals("MIN")||name.equals("MAX"))&&arguments.size()==1;}
        public Object eval(Context context) {
            if(name.equals("IF"))return arguments.get(Boolean.TRUE.equals(logical(arguments.get(0).eval(context)))?1:2).eval(context);
            if(name.equals("COALESCE")){for(Node argument:arguments){Object value=argument.eval(context);if(value!=null)return value;}return null;}
            if(name.equals("ROUND")){
                Object value=arguments.get(0).eval(context);if(value==null)return null;
                int scale;try{scale=number(arguments.get(1).eval(context)).intValueExact();}catch(ArithmeticException e){throw ReportFault.invalid("ROUND 精度必须为整数");}
                if(scale<0||scale>12)throw ReportFault.invalid("ROUND 精度范围为 0–12");
                return checked(number(value).setScale(scale,RoundingMode.HALF_UP));
            }
            if(ownAggregate()){
                if(context.cache.containsKey(this))return context.cache.get(this);
                Object result=aggregateValue(context);context.cache.put(this,result);return result;
            }
            BigDecimal result=null;
            for(Node argument:arguments){Object value=argument.eval(context);if(value!=null){BigDecimal number=number(value);result=result==null?number:name.equals("MIN")?result.min(number):result.max(number);}}
            return result;
        }
        private Object aggregateValue(Context context){
            if(name.equals("COUNT")&&arguments.isEmpty())return BigDecimal.valueOf(context.rows.size());
            long count=0;BigDecimal result=null;
            for(Map<String,Object> row:context.rows){
                Object value=arguments.get(0).eval(new Context(row,context.rows,context.cache));if(value==null)continue;
                if(name.equals("COUNT")){count++;continue;}
                BigDecimal number=number(value);
                result=result==null?number:name.equals("SUM")?checked(result.add(number)):name.equals("MIN")?result.min(number):result.max(number);
            }
            return name.equals("COUNT")?BigDecimal.valueOf(count):result;
        }
        public void dependencies(Set<String> names) { arguments.forEach(node->node.dependencies(names)); }
        public void numericDependencies(Set<String> names){if(name.equals("COUNT")&&(arguments.isEmpty()||arguments.get(0) instanceof Variable))return;arguments.forEach(node->node.numericDependencies(names));}
        public void measureDependencies(Set<String> names){if(name.equals("SUM")||(name.equals("MIN")||name.equals("MAX"))&&arguments.size()==1)arguments.forEach(node->node.dependencies(names));arguments.forEach(node->node.measureDependencies(names));}
        public boolean aggregate() { return name.equals("SUM")||name.equals("COUNT")||(name.equals("MIN")||name.equals("MAX"))&&arguments.size()==1||arguments.stream().anyMatch(Node::aggregate); }
    }
    private static BigDecimal number(Object value) {
        if(value instanceof BigDecimal decimal)return checked(decimal);
        if(value instanceof Number number){try{return checked(new BigDecimal(number.toString()));}catch(NumberFormatException e){throw ReportFault.invalid("公式数值无效");}}
        throw ReportFault.invalid("公式仅支持数值字段");
    }
    private static Boolean logical(Object value){return value==null?null:bool(value);}
    private static boolean bool(Object value) { if(value instanceof Boolean b)return b;throw ReportFault.invalid("逻辑运算需要比较结果或布尔值"); }
    static BigDecimal checked(BigDecimal value) {
        if(value.precision()>38||(long)value.precision()-Math.min(value.scale(),0)>38||value.scale() < -64||value.scale()>64)throw ReportFault.invalid("公式数值超出精度范围");return value;
    }
    Set<String> aggregateDependencies(){var names=new TreeSet<String>();aggregateDependencies(root,names);return Set.copyOf(names);}
    private static void aggregateDependencies(Node node,Set<String> names){
        if(node instanceof Function function){if(function.ownAggregate())function.dependencies(names);function.arguments.forEach(argument->aggregateDependencies(argument,names));}
        else if(node instanceof Unary unary)aggregateDependencies(unary.value,names);
        else if(node instanceof Binary binary){aggregateDependencies(binary.left,names);aggregateDependencies(binary.right,names);}
    }
    ValueType validateTypes(Map<String,ValueType> fields){
        Type result=type(root,fields);if(result==Type.TEXT)throw ReportFault.invalid("计算列仅支持数值或布尔结果");
        return result==Type.BOOLEAN?ValueType.BOOLEAN:ValueType.DECIMAL;
    }
    private static Type type(Node node,Map<String,ValueType> fields){
        if(node instanceof Literal literal)return literal.value==null?Type.NULL:literal.value instanceof Boolean?Type.BOOLEAN:Type.NUMBER;
        if(node instanceof Variable variable){
            ValueType value=fields.get(variable.name);if(value==null)throw ReportFault.invalid("公式字段类型缺失");
            return value==ValueType.DECIMAL||value==ValueType.INTEGER?Type.NUMBER:value==ValueType.BOOLEAN?Type.BOOLEAN:Type.TEXT;
        }
        if(node instanceof Unary unary){boolean logical=Set.of("NOT","!").contains(unary.operator);requireType(type(unary.value,fields),logical?Type.BOOLEAN:Type.NUMBER);return logical?Type.BOOLEAN:Type.NUMBER;}
        if(node instanceof Binary binary){
            Type left=type(binary.left,fields),right=type(binary.right,fields);
            if(Set.of("AND","&&","OR","||").contains(binary.operator)){requireType(left,Type.BOOLEAN);requireType(right,Type.BOOLEAN);return Type.BOOLEAN;}
            if(Set.of("=","==","!=").contains(binary.operator)){
                compatible(left,right);if(left==Type.TEXT||right==Type.TEXT)throw ReportFault.invalid("比较仅支持数值或布尔值");return Type.BOOLEAN;
            }
            requireType(left,Type.NUMBER);requireType(right,Type.NUMBER);return Set.of("<",">","<=",">=").contains(binary.operator)?Type.BOOLEAN:Type.NUMBER;
        }
        Function function=(Function)node;
        if(function.name.equals("COUNT")){function.arguments.forEach(argument->type(argument,fields));return Type.NUMBER;}
        if(function.name.equals("IF")){
            requireType(type(function.arguments.get(0),fields),Type.BOOLEAN);
            Type result=compatible(type(function.arguments.get(1),fields),type(function.arguments.get(2),fields));
            if(result==Type.TEXT)throw ReportFault.invalid("条件分支仅支持数值或布尔值");return result;
        }
        if(function.name.equals("COALESCE")){
            Type result=Type.NULL;for(Node argument:function.arguments)result=compatible(result,type(argument,fields));
            if(result==Type.TEXT)throw ReportFault.invalid("空值替代仅支持数值或布尔值");return result;
        }
        for(Node argument:function.arguments)requireType(type(argument,fields),Type.NUMBER);return Type.NUMBER;
    }
    private static void requireType(Type actual,Type expected){if(actual!=Type.NULL&&actual!=expected)throw ReportFault.invalid("公式操作数类型不匹配");}
    private static Type compatible(Type left,Type right){if(left==Type.NULL)return right;if(right==Type.NULL)return left;if(left!=right)throw ReportFault.invalid("公式分支或比较类型不匹配");return left;}
    private static final class Parser {
        private final List<String> tokens=new ArrayList<>();private int position;private int depth;private int nodes;
        Parser(String input) {
            int i=0;
            while(i<input.length()) {
                char c=input.charAt(i);if(Character.isWhitespace(c)){i++;continue;}
                if(Character.isDigit(c)||c=='.'&&i+1<input.length()&&Character.isDigit(input.charAt(i+1))) {
                    int start=i++;while(i<input.length()&&(Character.isDigit(input.charAt(i))||input.charAt(i)=='.'))i++;
                    String number=input.substring(start,i);if(!number.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))throw ReportFault.invalid("非法数字");tokens.add(number);
                } else if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c=='_') {
                    int start=i++;while(i<input.length()&&(Character.isLetterOrDigit(input.charAt(i))||input.charAt(i)=='_'))i++;
                    String word=input.substring(start,i);if(word.length()>64)throw ReportFault.invalid("公式字段名过长");tokens.add(word);
                } else {
                    String two=i+1<input.length()?input.substring(i,i+2):"";
                    if(Set.of("<=",">=","==","!=","&&","||").contains(two)){tokens.add(two);i+=2;}
                    else if("+-*/%()!,<>=,".indexOf(c)>=0){tokens.add(String.valueOf(c));i++;}
                    else throw ReportFault.invalid("公式包含禁止的字符");
                }
                if(tokens.size()>256)throw ReportFault.invalid("公式过于复杂");
            }
            tokens.add("EOF");
        }
        String peek(){return position<tokens.size()?tokens.get(position):"EOF";}
        boolean match(String...values){for(String v:values)if(peek().equals(v)){position++;return true;}return false;}
        void expect(String value){if(!match(value))throw ReportFault.invalid("公式括号或参数不完整");}
        Node count(Node node){if(++nodes>256)throw ReportFault.invalid("公式过于复杂");return node;}
        Node expression(){return or();}
        Node or(){Node n=and();while(match("OR","||"))n=count(new Binary(tokens.get(position-1),n,and()));return n;}
        Node and(){Node n=comparison();while(match("AND","&&"))n=count(new Binary(tokens.get(position-1),n,comparison()));return n;}
        Node comparison(){Node n=addition();if(match("=","==","!=","<",">","<=",">="))n=count(new Binary(tokens.get(position-1),n,addition()));return n;}
        Node addition(){Node n=multiply();while(match("+","-"))n=count(new Binary(tokens.get(position-1),n,multiply()));return n;}
        Node multiply(){Node n=unary();while(match("*","/","%"))n=count(new Binary(tokens.get(position-1),n,unary()));return n;}
        Node unary(){if(++depth>32)throw ReportFault.invalid("公式嵌套过深");try{if(match("+","-","!","NOT"))return count(new Unary(tokens.get(position-1),unary()));return primary();}finally{depth--;}}
        Node primary(){
            if(match("(")){Node n=expression();expect(")");return n;}
            String word=peek();if(word.equals("EOF"))throw ReportFault.invalid("缺少公式值");position++;
            if(word.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))return count(new Literal(checked(new BigDecimal(word))));
            if(word.equals("NULL"))return count(new Literal(null));if(word.equals("TRUE"))return count(new Literal(true));if(word.equals("FALSE"))return count(new Literal(false));
            if(!word.matches("[A-Za-z_][A-Za-z0-9_]*"))throw ReportFault.invalid("缺少公式值");
            if(!match("("))return count(new Variable(word));
            String name=word.toUpperCase(Locale.ROOT);if(!Set.of("IF","COALESCE","ROUND","SUM","COUNT","MIN","MAX").contains(name))throw ReportFault.invalid("未支持的公式函数");
            List<Node> arguments=new ArrayList<>();if(!match(")")){do{if(arguments.size()>=8)throw ReportFault.invalid("函数参数过多");arguments.add(expression());}while(match(","));expect(")");}
            int size=arguments.size();boolean valid=switch(name){case "IF"->size==3;case "ROUND"->size==2;case "SUM"->size==1;case "COUNT"->size<=1;case "COALESCE"->size>=2;default->size>=1;};
            if(!valid)throw ReportFault.invalid("函数参数数量不正确");
            boolean ownAggregate=name.equals("SUM")||name.equals("COUNT")||(name.equals("MIN")||name.equals("MAX"))&&size==1;
            if(ownAggregate&&arguments.stream().anyMatch(Node::aggregate))throw ReportFault.invalid("不支持嵌套聚合函数");return count(new Function(name,List.copyOf(arguments)));
        }
    }
}
