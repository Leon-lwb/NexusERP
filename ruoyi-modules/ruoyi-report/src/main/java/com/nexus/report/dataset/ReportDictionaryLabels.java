package com.nexus.report.dataset;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.service.DictService;
import com.nexus.common.report.ReportActor;
import com.nexus.common.tenant.helper.TenantHelper;

import java.util.*;

/** 在系统数据源和执行者租户内读取字典，不把ERP流式连接用于跨库查询。 */
@DS("master")
public class ReportDictionaryLabels {
    private final DictService dictionaries;

    /**
     * 复用系统字典的租户缓存和标签口径。
     *
     * @param dictionaries 系统字典服务
     */
    public ReportDictionaryLabels(DictService dictionaries) {
        this.dictionaries = dictionaries;
    }

    /**
     * 为一次取数复制字典名称，异步运行显式限定执行者租户。
     *
     * @param actor 当前执行者
     * @param types 已授权投影涉及的固定字典类型
     * @return 不可变的字典名称集合
     */
    public Map<String, Map<String, String>> load(ReportActor actor, Set<String> types) {
        return TenantHelper.dynamic(actor.tenantId(), () -> {
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            for (String type : types) {
                Map<String, String> values = new LinkedHashMap<>();
                var source = dictionaries.getAllDictByDictType(type);
                if (source != null) source.forEach((code, label) -> {
                    if (code != null && label != null && !label.isBlank()) values.put(code, label);
                });
                result.put(type, Collections.unmodifiableMap(values));
            }
            return Collections.unmodifiableMap(result);
        });
    }
}
