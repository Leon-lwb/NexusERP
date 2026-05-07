package org.dromara.online.service;

import org.dromara.generator.domain.GenTableDesign;

import java.util.List;

/**
 * 在线表单设计Service接口
 *
 * @author wanghuan
 */
public interface ITableDesignService {
    Long saveOrUpdate(GenTableDesign design);
    List<GenTableDesign> list(String keyword);
    GenTableDesign getById(Long id);
    void removeById(Long id);

    String generateSql(String designJson);
    void createTables(String sql, boolean overwriteIfExists);

    void importToGen(String designJson, String dataName, boolean overwriteIfExists);
}
