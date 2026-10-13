package com.nexus.report.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="nexus.report")
public class ReportProperties {
    private String artifactRoot="./data/report";private String fontPath;private int workers=4;private int retentionHours=24;private int taskSeconds=240;private long maxArtifactBytes=100L*1024*1024;
    public String getArtifactRoot(){return artifactRoot;}public void setArtifactRoot(String value){artifactRoot=value;}
    public String getFontPath(){return fontPath;}public void setFontPath(String value){fontPath=value;}
    public int getWorkers(){return workers;}public void setWorkers(int value){if(value<1||value>8)throw new IllegalArgumentException("Report worker range 1-8");workers=value;}
    public int getRetentionHours(){return retentionHours;}public void setRetentionHours(int value){if(value<1||value>168)throw new IllegalArgumentException("Report retention range 1-168 hours");retentionHours=value;}
    public int getTaskSeconds(){return taskSeconds;}public void setTaskSeconds(int value){if(value<30||value>600)throw new IllegalArgumentException("Report task timeout range 30-600 seconds");taskSeconds=value;}
    public long getMaxArtifactBytes(){return maxArtifactBytes;}public void setMaxArtifactBytes(long value){if(value<1024*1024||value>200L*1024*1024)throw new IllegalArgumentException("Report artifact size range 1-200 MB");maxArtifactBytes=value;}
}
