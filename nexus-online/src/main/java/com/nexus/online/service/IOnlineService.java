package com.nexus.online.service;

import com.nexus.common.core.page.TableDataInfo;

import java.util.List;
import java.util.Map;

public interface IOnlineService {
    TableDataInfo listTableData(String tableName, int pageNum, int pageSize);
    Map<String, Object> getDetail(String tableName, String id);
    int upsertByName(String tableName, Map<String, Object> data);
    int deleteByIds(String tableName, List<String> ids);
}
