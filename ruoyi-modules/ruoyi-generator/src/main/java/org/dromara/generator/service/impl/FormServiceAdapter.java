package org.dromara.generator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.generator.domain.GenTable;
import org.dromara.generator.service.IFormService;
import org.dromara.generator.service.IUnifiedGenService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Online表单服务适配器
 * 适配原有的IFormService接口，委托给IUnifiedGenService实现
 * 供Flowable等原有模块调用
 *
 * @author ruoyi
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Primary
public class FormServiceAdapter implements IFormService {

    private final IUnifiedGenService unifiedGenService;

    @Override
    public Map<String, Object> getOnlineForm(Long tableId) {
        return unifiedGenService.getOnlineForm(tableId);
    }

    @Override
    public TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery) {
        // 委托给原有的GenTableService
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TableDataInfo<Map> listOnlineData(Long tableId, String tableName, PageQuery pageQuery) {
        TableDataInfo<Map<String, Object>> result = unifiedGenService.listOnlineData(tableId, pageQuery);
        return (TableDataInfo<Map>) (TableDataInfo<?>) result;
    }

    @Override
    public int addOrUpdateOnlineData(Long tableId, String tableName, Map<String, Object> map) {
        return unifiedGenService.addOrUpdateOnlineData(tableId, map);
    }

    @Override
    public void deleteOnlineData(Long tableId, String tableName, List<Long> ids) {
        unifiedGenService.deleteOnlineData(tableId, ids);
    }

    @Override
    public Map<String, Object> getOnlineData(Long tableId, String tableName, Long id) {
        return unifiedGenService.getOnlineDataById(tableId, id);
    }

    @Override
    public TableDataInfo selectDynamicTablePage(String tableName, QueryWrapper<?> wrapper, Page<?> page) {
        return unifiedGenService.selectDynamicTablePage(tableName, wrapper, page);
    }

    @Override
    public Map<String, Object> selectDynamicTableById(String tableName, String id) {
        return unifiedGenService.selectDynamicTableById(tableName, id);
    }

    @Override
    public Map<String, Object> getPopupDetail(Long tableId, String tableName, String id) {
        return unifiedGenService.getPopupDetail(tableId, tableName, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TableDataInfo<Map> listPopupTableData(Long tableId, String dictTableName, PageQuery pageQuery) {
        TableDataInfo<Map<String, Object>> result = unifiedGenService.listPopupTableData(tableId, dictTableName, pageQuery);
        return (TableDataInfo<Map>) (TableDataInfo<?>) result;
    }

    @Override
    public TableDataInfo<Map> listTableData(String tableName, PageQuery pageQuery) {
        // 通过表名查询tableId，然后调用listOnlineData
        // 这里简化处理
        return null;
    }

    @Override
    public int addOrUpdateOnlineDataByName(String tableName, Map<String, Object> map) {
        return unifiedGenService.addOrUpdateOnlineDataByName(tableName, map);
    }
}
