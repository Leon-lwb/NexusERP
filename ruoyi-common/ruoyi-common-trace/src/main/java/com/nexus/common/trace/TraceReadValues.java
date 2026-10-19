package com.nexus.common.trace;
import java.math.*;
import java.util.*;
/** Preserve business identifiers and decimals even when a section is stored as generic JSON. */
public final class TraceReadValues {
    private TraceReadValues(){}
    public static Map<String,Object> wire(Map<String,Object> row){
        var result=new LinkedHashMap<String,Object>();row.forEach((key,value)->result.put(key.toLowerCase(Locale.ROOT),
            value instanceof BigDecimal decimal?decimal.toPlainString():value instanceof Long || value instanceof BigInteger?value.toString():value));
        return Collections.unmodifiableMap(result);
    }
    public static Long id(Object value){return value==null?null:((Number)value).longValue();}
}
