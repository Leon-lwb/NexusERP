package com.nexus.online.service;

import com.nexus.common.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;

import java.util.List;
import java.util.Map;

public interface IOnlineService {
    TableDataInfo listTableData(String tableName, int pageNum, int pageSize);
    TableDataInfo listOnlineData(Long tableId, String tableName, int pageNum, int pageSize);
    Map<String, Object> getDetail(String tableName, String id);
    Map<String, Object> getOnlineData(Long tableId, String tableName, Long id);
    int upsertByName(String tableName, Map<String, Object> data);
    int deleteByIds(String tableName, List<String> ids);
    GenTable getOnlineForm(Long tableId);
    Map<String, Object> getOnlineFormData(Long tableId);
}
