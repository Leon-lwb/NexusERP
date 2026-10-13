package com.nexus.common.report;
/** Defaults are registry-owned literals or the bounded TODAY/MONTHS_AGO_6 date tokens. */
public record DatasetParameter(String name,String label,ValueType type,boolean required,String defaultValue) {
    public DatasetParameter(String name,String label,ValueType type,boolean required){this(name,label,type,required,null);}
}
