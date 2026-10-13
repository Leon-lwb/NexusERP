package com.nexus.report.engine;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import java.math.*;
import java.util.*;
/** Typed presentation computation; never writes business facts back to providers. */
public final class ReportCalculator {
    public record SummaryValue(String label,Map<String,TypedValue> group,TypedValue value) {
        public SummaryValue { group=Collections.unmodifiableMap(new LinkedHashMap<>(group)); }
    }
    public record CalculatedData(List<Map<String,TypedValue>> rows,List<SummaryValue> summaries) {
        public CalculatedData { rows=rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();summaries=List.copyOf(summaries); }
    }
    public CalculatedData calculate(CompiledReport report,List<Map<String,Object>> source,Map<String,Object> parameters){
        if(source.size()>100_000||report.aggregateExpressions()&&source.size()>5000)throw ReportFault.capacity();
        long work=(long)source.size()*report.expressions().values().stream().mapToInt(SafeExpression::cost).sum();if(work>2_000_000)throw ReportFault.capacity();
        Map<String,Object> parameterValues=parameterVariables(report,parameters);
        Map<String,ValueType> resultTypes=expressionTypes(report);
        List<Map<String,TypedValue>> rows=new ArrayList<>();List<Map<String,Object>> values=new ArrayList<>();
        long characters=0;
        for(Map<String,Object> raw:source){
            Map<String,TypedValue> typed=typedRow(report,raw);
            for(TypedValue value:typed.values())if(value.value()!=null){characters+=value.value().length();if(characters>10_000_000)throw ReportFault.capacity();}
            rows.add(typed);values.add(variables(typed,parameterValues));
        }
        for(String name:report.computedOrder()){
            SafeExpression.Evaluation evaluation=report.expressions().get(name).session(values);
            for(int i=0;i<rows.size();i++){
                Object computed=evaluation.evaluate(values.get(i));
                TypedValue typed=TypedValue.of(resultTypes.get(name),computed);
                rows.get(i).put(name,typed);values.get(i).put(name,scalar(typed));
            }
        }
        var accumulator=new SummaryAccumulator(report);rows.forEach(accumulator::accept);
        return new CalculatedData(rows,accumulator.finish());
    }
    public Map<String,TypedValue> calculateRow(CompiledReport report,Map<String,Object> source,Map<String,Object> parameters){
        if(report.aggregateExpressions())throw ReportFault.invalid("聚合计算需要有界快照");
        Map<String,Object> parameterValues=parameterVariables(report,parameters);
        Map<String,ValueType> resultTypes=expressionTypes(report);
        Map<String,TypedValue> result=typedRow(report,source);Map<String,Object> variables=variables(result,parameterValues);
        for(String name:report.computedOrder()){
            Object computed=report.expressions().get(name).evaluate(variables,List.of(variables));
            TypedValue typed=TypedValue.of(resultTypes.get(name),computed);result.put(name,typed);variables.put(name,scalar(typed));
        }
        return result;
    }
    private static Map<String,ValueType> expressionTypes(CompiledReport report){
        Map<String,ValueType> types=new HashMap<>();report.descriptor().fields().forEach(field->types.put(field.name(),field.type()));
        report.descriptor().parameters().forEach(parameter->types.put("p_"+parameter.name(),parameter.type()));
        for(String name:report.computedOrder())types.put(name,report.expressions().get(name).validateTypes(types));return types;
    }
    private static Map<String,TypedValue> typedRow(CompiledReport report,Map<String,Object> source){
        Map<String,TypedValue> row=new LinkedHashMap<>();
        for(String name:report.requiredFields()){
            if(!source.containsKey(name))throw new ReportFault("MISSING_RESULT_FIELD","数据集缺少必需字段: "+name);
            row.put(name,typed(report.descriptor().field(name).type(),source.get(name),name));
        }
        return row;
    }
    private static TypedValue typed(ValueType type,Object raw,String name){
        if(raw!=null&&raw.toString().length()>10_000)throw ReportFault.capacity();
        try{
            if(raw!=null&&(type==ValueType.DECIMAL||type==ValueType.INTEGER))SafeExpression.checked(new BigDecimal(raw.toString()));
            return TypedValue.of(type,raw);
        }catch(IllegalArgumentException|java.time.DateTimeException e){throw new ReportFault("INVALID_DATA_TYPE","数据集字段或参数类型不匹配: "+name);}
    }
    private static Map<String,Object> parameterVariables(CompiledReport report,Map<String,Object> parameters){
        if(parameters==null)throw ReportFault.invalid("参数必须明确");
        Set<String> names=new HashSet<>();report.descriptor().parameters().forEach(parameter->names.add(parameter.name()));
        if(!names.containsAll(parameters.keySet()))throw ReportFault.invalid("包含未登记的报表参数");
        Set<String> required=new HashSet<>();
        report.descriptor().parameters().stream().filter(DatasetParameter::required).forEach(parameter->required.add(parameter.name()));
        report.dsl().parameters().stream().filter(ReportDsl.Parameter::required).forEach(parameter->required.add(parameter.name()));
        for(String name:required)if(parameters.get(name)==null)throw ReportFault.invalid("缺少必填报表参数: "+name);
        Map<String,Object> result=new LinkedHashMap<>();
        for(DatasetParameter parameter:report.descriptor().parameters())result.put("p_"+parameter.name(),scalar(typed(parameter.type(),parameters.get(parameter.name()),parameter.name())));
        return result;
    }
    private static Map<String,Object> variables(Map<String,TypedValue> row,Map<String,Object> parameters){
        Map<String,Object> result=new LinkedHashMap<>();row.forEach((name,value)->result.put(name,scalar(value)));result.putAll(parameters);return result;
    }
    private static Object scalar(TypedValue value){
        if(value==null||value.value()==null)return null;
        return switch(value.type()){case DECIMAL,INTEGER->new BigDecimal(value.value());case BOOLEAN->Boolean.valueOf(value.value());default->value.value();};
    }
    public static String display(TypedValue value,int scale){
        if(value==null||value.value()==null)return "";
        return value.type()==ValueType.DECIMAL?new BigDecimal(value.value()).setScale(scale,RoundingMode.HALF_UP).toPlainString():value.value();
    }
    public static final class SummaryAccumulator {
        private final CompiledReport report;
        private final List<LinkedHashMap<List<TypedValue>,Bucket>> groups=new ArrayList<>();
        public SummaryAccumulator(CompiledReport report){this.report=report;report.dsl().summaries().forEach(s->groups.add(new LinkedHashMap<>()));}
        public void accept(Map<String,TypedValue> row){
            for(int i=0;i<report.dsl().summaries().size();i++){
                var definition=report.dsl().summaries().get(i);
                for(String name:definition.groupBy())if(!row.containsKey(name))throw new ReportFault("MISSING_RESULT_FIELD","汇总缺少分组字段: "+name);
                if(!definition.function().equals("COUNT_ROWS")&&!row.containsKey(definition.field()))throw new ReportFault("MISSING_RESULT_FIELD","汇总缺少必需字段: "+definition.field());
                var key=definition.groupBy().stream().map(name->groupValue(row,name)).toList();
                var group=groups.get(i);if(group.size()>=10_000&&!group.containsKey(key))throw ReportFault.capacity();
                var bucket=group.computeIfAbsent(key,k->new Bucket());bucket.rows++;
                if(definition.function().equals("COUNT_ROWS"))continue;
                TypedValue value=row.get(definition.field());if(value==null||value.value()==null)continue;bucket.present++;
                if(Set.of("SUM","MIN","MAX").contains(definition.function())){
                    BigDecimal number=summaryNumber(value);
                    bucket.value=bucket.value==null?number:definition.function().equals("SUM")?bucket.value.add(number):definition.function().equals("MIN")?bucket.value.min(number):bucket.value.max(number);
                    SafeExpression.checked(bucket.value);
                }
            }
        }
        private TypedValue groupValue(Map<String,TypedValue> row,String name){
            TypedValue value=row.get(name);if(value==null)return TypedValue.of(report.descriptor().field(name).type(),null);
            return value.type()==ValueType.DECIMAL&&value.value()!=null?TypedValue.of(ValueType.DECIMAL,summaryNumber(value).stripTrailingZeros()):value;
        }
        private static BigDecimal summaryNumber(TypedValue value){
            try{return SafeExpression.checked(new BigDecimal(value.value()));}catch(NumberFormatException e){throw new ReportFault("INVALID_DATA_TYPE","汇总度量类型无效");}
        }
        public List<SummaryValue> finish(){
            List<SummaryValue> result=new ArrayList<>();
            for(int i=0;i<report.dsl().summaries().size();i++){
                var definition=report.dsl().summaries().get(i);var group=groups.get(i);
                if(group.isEmpty()&&definition.groupBy().isEmpty())group.put(List.of(),new Bucket());
                for(var entry:group.entrySet()){
                    Map<String,TypedValue> keys=new LinkedHashMap<>();for(int j=0;j<definition.groupBy().size();j++)keys.put(definition.groupBy().get(j),entry.getKey().get(j));
                    Object value=switch(definition.function()){case "COUNT_ROWS"->entry.getValue().rows;case "COUNT"->entry.getValue().present;default->entry.getValue().value==null?null:entry.getValue().value.setScale(definition.scale(),RoundingMode.HALF_UP);};
                    result.add(new SummaryValue(definition.label(),Collections.unmodifiableMap(keys),TypedValue.of(definition.function().startsWith("COUNT")?ValueType.INTEGER:ValueType.DECIMAL,value)));
                }
            }
            return result;
        }
        private static final class Bucket{long rows;long present;BigDecimal value;}
    }
}
