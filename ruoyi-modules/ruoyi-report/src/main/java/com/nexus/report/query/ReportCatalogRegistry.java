package com.nexus.report.query;

import com.nexus.common.report.catalog.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.service.ReportDefinitionService;

import java.util.*;

/** 保存业务模块安装的固定目录，目录同版本变化时拒绝历史查询。 */
public final class ReportCatalogRegistry {
    /** 保存当前catalogs状态或配置。 */
    private final Map<Integer, ReportEntityCatalog> catalogs;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /**
     * 冻结已安装目录并拒绝重复版本。
     *
     * @param providers 当前契约的providers配置或状态
     * @param json 固定版本的契约正文
     */
    public ReportCatalogRegistry(List<ReportEntityCatalogProvider> providers, ReportCompiler json) {
        this.json = json;
        var map = new TreeMap<Integer, ReportEntityCatalog>();
        for (var provider : providers)
            if (map.putIfAbsent(provider.catalog().version(), provider.catalog()) != null)
                throw new IllegalStateException("查询目录版本重复");
        catalogs = Map.copyOf(map);
    }

    /**
     * 精确读取目录版本，不自动回退最新版本。
     *
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportEntityCatalog require(int version) {
        var catalog = catalogs.get(version);
        if (catalog == null) throw new ReportFault("CATALOG_UNSUPPORTED", "所需查询目录版本未安装", 409);
        return catalog;
    }

    /**
     * 返回固定目录结构、字段、关系及策略的校验和。
     *
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public String checksum(int version) {
        return ReportDefinitionService.hash(json.json(require(version)));
    }

    /**
     * 读取已安装目录版本，接口仅投影逻辑信息。
     *
     * @return 当前受控处理的固定结果
     */
    public List<Integer> versions() {
        return catalogs.keySet().stream().sorted().toList();
    }
}
