package com.nexus.common.report;
import java.util.*;
/** Captured server-side only; no bearer token or request ThreadLocal crosses tasks. */
public record ReportExecutionContext(ReportActor actor,List<ReportScope> scopes,ReportCancellation cancellation) {
    public ReportExecutionContext { Objects.requireNonNull(actor);scopes=List.copyOf(scopes);Objects.requireNonNull(cancellation);if(scopes.isEmpty())throw new IllegalArgumentException("Query scope required"); }
    public ReportExecutionContext(ReportActor actor,List<ReportScope> scopes){this(actor,scopes,new ReportCancellation());}
    public String tenantId(){return actor.tenantId();}
}
