package com.nexus.common.report;
import java.util.*;
import java.util.function.Consumer;
/** Read-only SPI. Providers own business facts; presentation stays in the report engine. */
public interface ReportDatasetProvider {
    DatasetDescriptor descriptor();
    DatasetResult query(ReportExecutionContext context,DatasetQuery query);
    default void stream(ReportExecutionContext context,DatasetQuery query,Consumer<Map<String,Object>> consumer){
        DatasetResult result=query(context,new DatasetQuery(query.parameters(),query.fields(),1,5001,true,query.sortField(),query.descending()));
        if(result.total()>5000)throw new DatasetAccessException("CAPACITY_EXCEEDED","该扩展数据集未实现大规模流式读取");
        result.rows().forEach(row->{context.cancellation().check();consumer.accept(row);});
    }
}
