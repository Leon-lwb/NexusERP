package com.nexus.common.report;
import java.util.*;
public record DatasetField(String name,String label,ValueType type,String permission,List<String> aggregationKeys,Boolean aggregatable) {
    public DatasetField { aggregationKeys=List.copyOf(aggregationKeys==null?List.of():aggregationKeys);aggregatable=aggregatable==null?true:aggregatable; }
    public DatasetField(String name,String label,ValueType type,String permission,List<String> aggregationKeys){this(name,label,type,permission,aggregationKeys,true);}
    public DatasetField(String name,String label,ValueType type){this(name,label,type,null,List.of(),true);}
}
