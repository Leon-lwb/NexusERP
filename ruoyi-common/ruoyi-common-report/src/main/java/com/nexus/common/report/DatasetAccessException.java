package com.nexus.common.report;
/** Only stable error codes leave the provider boundary; no database error/parameter values. */
public final class DatasetAccessException extends IllegalArgumentException {
    private final String code;
    public DatasetAccessException(String code,String message){super(message);this.code=code;}
    public String code(){return code;}
}
