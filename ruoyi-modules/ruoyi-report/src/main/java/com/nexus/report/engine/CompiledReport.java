package com.nexus.report.engine;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;
import java.util.*;
public record CompiledReport(ReportDsl dsl,DatasetDescriptor descriptor,Set<String> requiredFields,
    Map<String,SafeExpression> expressions,List<String> computedOrder,List<ReportScope> scopes) {
    public CompiledReport { requiredFields=Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields));expressions=Collections.unmodifiableMap(new LinkedHashMap<>(expressions));computedOrder=List.copyOf(computedOrder);scopes=List.copyOf(scopes); }
    public boolean aggregateExpressions(){return expressions.values().stream().anyMatch(SafeExpression::aggregate);}
    public ReportExecutionContext context(ReportActor actor){
        if(!actor.has(descriptor.permission()))throw ReportFault.denied();
        List<ReportScope> executionScopes=new ArrayList<>();executionScopes.add(actor.scopeFor(descriptor.permission()));
        Set<String> permissions=new TreeSet<>();
        for(String name:requiredFields){String permission=descriptor.field(name).permission();if(permission!=null&&!permission.isBlank())permissions.add(permission);}
        for(String permission:permissions){if(!actor.has(permission))throw ReportFault.denied();executionScopes.add(actor.scopeFor(permission));}
        return new ReportExecutionContext(actor,executionScopes);
    }
}
