package com.nexus.report.domain;
import java.time.Instant;
public final class ReportRecords {
    private ReportRecords() { }
    public record Catalog(String id,String parentId,String name,long owner) { }
    public record Definition(String id,String tenant,String code,String name,String kind,String catalogId,String json,long revision,String publishedVersion,String status,long owner,Instant updatedAt) { }
    public record Version(String id,String reportId,int number,int schema,String engine,String json,String checksum,String datasetVersion,long owner,Instant createdAt) { }
    public record Dataset(String id,String tenant,String code,String name,String provider,String publishedVersion,String status,long owner) { }
    public record DatasetVersion(String id,String datasetId,int number,String provider,int providerVersion,String json,String checksum,long owner) { }
    public record Grant(String subjectType,String subjectId,String action) { }
    public record Run(String id,String tenant,long actor,String reportId,String version,String mode,String state,String parameters,String scopeHash,String snapshot,String plan,long rows,Instant dataTime,Instant expires,String error) { }
    public record ExportJob(String id,String tenant,long actor,String runId,String format,String requestKey,String requestHash,String state,int attempts,String leaseOwner,Instant leaseUntil,boolean cancelled,String file,Long bytes,Instant expires,String error) { }
}
