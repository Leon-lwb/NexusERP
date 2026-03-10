package com.nexus.online.controller;

import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.service.IGenTableService;
import com.nexus.online.service.IOnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/online")
public class OnlineController extends BaseController {

    @Autowired(required = false)
    private IGenTableService genTableService;

    @Autowired
    private IOnlineService onlineService;

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/{tableId}")
    public AjaxResult getOnlineForm(@PathVariable("tableId") Long tableId) {
        Map<String, Object> formData = onlineService.getOnlineFormData(tableId);
        if (formData == null || formData.isEmpty()) {
            return error("表单数据不存在");
        }
        return success(formData);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/list")
    public TableDataInfo list(GenTable query) {
        startPage();
        if (genTableService == null) {
            TableDataInfo info = new TableDataInfo();
            info.setRows(Collections.emptyList());
            info.setTotal(0L);
            return info;
        }
        List<GenTable> list = genTableService.selectDbTableList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getDataNames")
    public AjaxResult getDataNames() {
        return success(Collections.emptyList());
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getTableData/{tableName}")
    public TableDataInfo getTableData(@PathVariable String tableName,
                                      @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                      @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return onlineService.listTableData(tableName, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getData/{tableId}/{tableName}")
    public TableDataInfo getData(@PathVariable Long tableId,
                                 @PathVariable String tableName,
                                 @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                 @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return onlineService.listOnlineData(tableId, tableName, pageNum, pageSize);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/addOrUpdateDataByName/{tableName}")
    public AjaxResult addOrUpdateByName(@PathVariable String tableName, @RequestBody Map<String, Object> body) {
        int rows = onlineService.upsertByName(tableName, body);
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/addOrUpdateData/{tableId}/{tableName}")
    public AjaxResult addOrUpdate(@PathVariable String tableId, @PathVariable String tableName, @RequestBody Map<String, Object> body) {
        int rows = onlineService.upsertByName(tableName, body);
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:remove')")
    @DeleteMapping("/delOnlineData/{tableId}/{tableName}")
    public AjaxResult delete(@PathVariable String tableId, @PathVariable String tableName, @RequestBody String ids) {
        String[] arr = ids == null ? new String[0] : ids.split(",");
        int rows = onlineService.deleteByIds(tableName, java.util.Arrays.asList(arr));
        return toAjax(rows);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/detail/{tableId}/{tableName}/{id}")
    public AjaxResult detail(@PathVariable String tableId, @PathVariable String tableName, @PathVariable String id) {
        return success(onlineService.getDetail(tableName, id));
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @PostMapping("/getOnlineData/{tableId}/{tableName}")
    public AjaxResult getOnlineData(@PathVariable Long tableId,
                                    @PathVariable String tableName,
                                    @RequestBody Long id) {
        Map<String, Object> data = onlineService.getOnlineData(tableId, tableName, id);
        if (data == null || data.isEmpty()) {
            return error("数据不存在");
        }
        return success(data);
    }
}
