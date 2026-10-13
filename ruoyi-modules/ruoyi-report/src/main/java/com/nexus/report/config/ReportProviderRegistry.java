package com.nexus.report.config;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import java.util.*;
/** Multiple contract versions can coexist; duplicate code/version fails startup. */
public final class ReportProviderRegistry {
    private final Map<String,NavigableMap<Integer,ReportDatasetProvider>> providers=new TreeMap<>();
    public ReportProviderRegistry(Collection<ReportDatasetProvider> list){
        for(var provider:list){var d=Objects.requireNonNull(provider.descriptor(),"Dataset descriptor");
            if(d.code()==null||!d.code().matches("[A-Z][A-Z0-9_]{0,63}")||d.version()<1||d.permission()==null||d.permission().isBlank()||d.fields().isEmpty()||d.fields().size()>60)throw new IllegalStateException("Invalid report dataset descriptor");
            Set<String> fields=new HashSet<>();for(var field:d.fields())if(!fields.add(field.name())||field.type()==null)throw new IllegalStateException("Duplicate or untyped report dataset field");
            if(!fields.containsAll(d.defaultColumns()))throw new IllegalStateException("Default report columns absent");
            if(providers.computeIfAbsent(d.code(),key->new TreeMap<>()).putIfAbsent(d.version(),provider)!=null)throw new IllegalStateException("Duplicate report provider: "+d.code()+" v"+d.version());
        }
    }
    public ReportDatasetProvider get(String code,int version){var versions=providers.get(code);var provider=versions==null?null:versions.get(version);if(provider==null)throw new ReportFault("VERSION_UNSUPPORTED","所需数据集契约版本未安装");return provider;}
    public ReportDatasetProvider latest(String code){var versions=providers.get(code);if(versions==null)throw new ReportFault("DATASET_UNAVAILABLE","数据集未安装",404);return versions.lastEntry().getValue();}
    public List<ReportDatasetProvider> latest(){return providers.values().stream().map(versions->versions.lastEntry().getValue()).toList();}
}
