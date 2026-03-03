package com.nexus.online.service;

import com.nexus.online.domain.GenTableDesign;

import java.util.List;

public interface ITableDesignService {
    Long saveOrUpdate(GenTableDesign design);
    List<GenTableDesign> list(String keyword);
    GenTableDesign getById(Long id);
    void removeById(Long id);

    String generateSql(String designJson);
    void createTables(String sql, boolean overwriteIfExists);

    void importToGen(String designJson, String dataName, boolean overwriteIfExists);
}
