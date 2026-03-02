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

    /**
     * 查询数据表清单（代理至生成器 DB 表列表）
     */
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

    /**
     * 获取数据源名称（预留，返回空列表）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getDataNames")
    public AjaxResult getDataNames() {
        return success(Collections.emptyList());
    }

    /**
     * 查询字典/关联表数据（占位实现，返回空集以保障前端不报错）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getTableData/{tableName}")
    public TableDataInfo getTableData(@PathVariable String tableName,
                                      @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                      @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return onlineService.listTableData(tableName, pageNum, pageSize);
    }

    /**
     * 查询 online 表数据（占位实现）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/getData/{tableId}/{tableName}")
    public TableDataInfo getData(@PathVariable String tableId,
                                 @PathVariable String tableName,
                                 @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                 @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return onlineService.listTableData(tableName, pageNum, pageSize);
    }

    /**
     * 新增或更新数据（占位实现）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/addOrUpdateDataByName/{tableName}")
    public AjaxResult addOrUpdateByName(@PathVariable String tableName, @RequestBody Map<String, Object> body) {
        int rows = onlineService.upsertByName(tableName, body);
        return toAjax(rows);
    }

    /**
     * 新增或更新数据（占位实现）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/addOrUpdateData/{tableId}/{tableName}")
    public AjaxResult addOrUpdate(@PathVariable String tableId, @PathVariable String tableName, @RequestBody Map<String, Object> body) {
        int rows = onlineService.upsertByName(tableName, body);
        return toAjax(rows);
    }

    /**
     * 删除数据（占位实现）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:remove')")
    @DeleteMapping("/delOnlineData/{tableId}/{tableName}")
    public AjaxResult delete(@PathVariable String tableId, @PathVariable String tableName, @RequestBody String ids) {
        String[] arr = ids == null ? new String[0] : ids.split(",");
        int rows = onlineService.deleteByIds(tableName, java.util.Arrays.asList(arr));
        return toAjax(rows);
    }

    /**
     * 行详情（占位实现）
     */
    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/detail/{tableId}/{tableName}/{id}")
    public AjaxResult detail(@PathVariable String tableId, @PathVariable String tableName, @PathVariable String id) {
        return success(onlineService.getDetail(tableName, id));
    }
}
