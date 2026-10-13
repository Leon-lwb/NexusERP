package com.nexus.common.report;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
/** Code-owned SELECT catalog. No API/template may supply SQL identifiers or connection strings. */
public abstract class JdbcDatasetProvider implements ReportDatasetProvider {
    private final JdbcTemplate jdbc;private final DatasetDescriptor descriptor;private final String from;private final String alias;private final String key;private final Map<String,String> expressions;
    protected JdbcDatasetProvider(JdbcTemplate jdbc,DatasetDescriptor descriptor,String from,String alias,String key,Map<String,String> expressions){
        this.jdbc=jdbc;this.descriptor=descriptor;this.from=from;this.alias=alias;this.key=key;this.expressions=Collections.unmodifiableMap(new LinkedHashMap<>(expressions));
        if(!expressions.keySet().containsAll(descriptor.fields().stream().map(DatasetField::name).toList()))throw new IllegalArgumentException("Projection catalog incomplete");
    }
    public DatasetDescriptor descriptor(){return descriptor;}
    protected record Filter(String sql,List<Object> values){public Filter{values=List.copyOf(values);}}
    protected List<Filter> filters(DatasetQuery query){return List.of();}
    protected static List<Filter> dates(DatasetQuery query,String expression){
        List<Filter> filters=new ArrayList<>();Object from=query.parameters().get("fromDate"),to=query.parameters().get("toDate");
        if(from!=null)filters.add(new Filter(expression+" >= ?",List.of(java.sql.Date.valueOf(from.toString()))));
        if(to!=null)filters.add(new Filter(expression+" < ?",List.of(java.sql.Date.valueOf(LocalDate.parse(to.toString()).plusDays(1)))));
        return filters;
    }
    protected static void equal(List<Filter> filters,DatasetQuery query,String parameter,String expression){Object value=query.parameters().get(parameter);if(value!=null)filters.add(new Filter(expression+" = ?",List.of(value)));}
    private record Query(String where,List<Object> values,DatasetQuery query,ReportExecutionContext context) { }
    private Query build(ReportExecutionContext context,DatasetQuery input){
        context.cancellation().check();DatasetQuery query=DatasetQueryValidator.validate(descriptor,input);
        if(!context.actor().has(descriptor.permission()))throw new DatasetAccessException("SCOPE_DENIED","缺少业务数据集权限");
        List<ReportScope> scopes=new ArrayList<>(context.scopes());scopes.add(context.actor().scopeFor(descriptor.permission()));
        for(String name:query.fields()){
            String permission=descriptor.field(name).permission();if(permission==null||permission.isBlank())continue;
            if(!context.actor().has(permission))throw new DatasetAccessException("SCOPE_DENIED","缺少字段权限");scopes.add(context.actor().scopeFor(permission));
        }
        var effective=new ReportExecutionContext(context.actor(),scopes,context.cancellation());var policy=ReportSqlScope.where(alias,effective);
        StringBuilder where=new StringBuilder(policy.sql());List<Object> values=new ArrayList<>(policy.parameters());
        for(Filter filter:filters(query)){where.append(" AND (").append(filter.sql()).append(")");values.addAll(filter.values());}
        return new Query(where.toString(),values,query,effective);
    }
    private String select(Query query){
        String columns=query.query.fields().isEmpty()?"1 AS _record":query.query.fields().stream().map(field->expressions.get(field)+" AS "+field).collect(Collectors.joining(","));
        String order=query.query.sortField()==null?key:expressions.get(query.query.sortField());
        return "SELECT "+columns+" FROM "+from+" WHERE "+query.where+" ORDER BY "+order+(query.query.descending()?" DESC":" ASC")+(query.query.sortField()==null?"":", "+key+" ASC")+" LIMIT ? OFFSET ?";
    }
    private PreparedStatementCreator statement(String sql,List<Object> values,ReportExecutionContext context,boolean streaming){
        return connection->{
            PreparedStatement statement=connection.prepareStatement(sql);statement.setQueryTimeout(streaming?120:10);
            if(streaming)statement.setFetchSize(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql")?Integer.MIN_VALUE:500);
            for(int i=0;i<values.size();i++)statement.setObject(i+1,values.get(i));
            context.cancellation().attach(()->{try{statement.cancel();}catch(SQLException ignored){}});return statement;
        };
    }
    private Map<String,Object> row(ResultSet result,Set<String> fields) throws SQLException {Map<String,Object> values=new LinkedHashMap<>();for(String field:fields)values.put(field,result.getObject(field));return values;}
    public DatasetResult query(ReportExecutionContext context,DatasetQuery input){
        Query query=build(context,input);Instant time=Instant.now();
        try{
            Long total=jdbc.query(statement("SELECT COUNT(*) FROM "+from+" WHERE "+query.where,query.values,query.context,false),result->result.next()?result.getLong(1):0L);
            context.cancellation().check();List<Object> values=new ArrayList<>(query.values);values.add(query.query.pageSize());values.add(query.query.snapshot()?0L:(long)(query.query.pageNumber()-1)*query.query.pageSize());
            List<Map<String,Object>> rows=jdbc.query(statement(select(query),values,query.context,false),(result,index)->row(result,query.query.fields()));
            return new DatasetResult(rows,total==null?0:total,time);
        }finally{context.cancellation().detach();}
    }
    public void stream(ReportExecutionContext context,DatasetQuery input,Consumer<Map<String,Object>> consumer){
        Query query=build(context,input);List<Object> values=new ArrayList<>(query.values);values.add(100001);values.add(0L);
        try{
            jdbc.query(statement(select(query),values,query.context,true),result->{int count=0;while(result.next()){context.cancellation().check();if(++count>100000)throw new DatasetAccessException("CAPACITY_EXCEEDED","明细超过十万行");consumer.accept(row(result,query.query.fields()));}return null;});
        }finally{context.cancellation().detach();}
    }
}
