package com.nexus.generator.controller;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.helper.DataBaseHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.generator.domain.GenTable;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.generator.domain.GenTableDesign;
import com.nexus.generator.service.IGenTableService;
import com.nexus.generator.service.IUnifiedGenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一代码生成与Online设计控制器
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/tool/gen")
public class UnifiedGenController extends BaseController {

    private final IUnifiedGenService unifiedGenService;
    private final IGenTableService genTableService;

    // ==================== 代码生成相关（原有功能） ====================

    /**
     * 查询代码生成列表
     */
    @GetMapping("/list")
    public TableDataInfo<GenTable> genList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageGenTableList(genTable, pageQuery);
    }

    /**
     * 查询数据库列表
     */
    @GetMapping("/db/list")
    public TableDataInfo<GenTable> dataList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageDbTableList(genTable, pageQuery);
    }

    /**
     * 查询数据表字段列表
     */
    @GetMapping(value = "/column/{tableId}")
    public R<Map<String, Object>> columnList(@PathVariable("tableId") Long tableId) {
        Map<String, Object> map = new HashMap<>();
        map.put("rows", genTableService.selectGenTableColumnListByTableId(tableId));
        map.put("info", genTableService.selectGenTableById(tableId));
        return R.ok(map);
    }

    /**
     * 导入表结构（保存）
     */
    @Log(title = "代码生成", businessType = BusinessType.IMPORT)
    @PostMapping("/importTable")
    public R<Void> importTableSave(String tables, String dataName) {
        String[] tableNames = StringUtils.split(tables, ",");
        if (tableNames == null || tableNames.length == 0) {
            throw new ServiceException("请选择要导入的表");
        }
        List<GenTable> tableList = genTableService.selectDbTableListByNames(tableNames, dataName);
        genTableService.importGenTable(tableList, dataName);
        return R.ok();
    }

    /**
     * 修改保存代码生成业务
     */
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> editSave(@RequestBody GenTable genTable) {
        genTableService.validateEdit(genTable);
        genTableService.updateGenTable(genTable);
        return R.ok();
    }

    /**
     * 删除代码生成
     */
    @Log(title = "代码生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tableIds}")
    public R<Void> remove(@PathVariable Long[] tableIds) {
        genTableService.deleteGenTableByIds(tableIds);
        return R.ok();
    }

    /**
     * 预览代码
     */
    @GetMapping("/preview/{tableId}")
    public R<Map<String, String>> preview(@PathVariable("tableId") Long tableId) {
        Map<String, String> dataMap = unifiedGenService.previewCode(tableId);
        return R.ok(dataMap);
    }

    /**
     * 生成代码（下载方式）
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/download/{tableId}")
    public void download(HttpServletResponse response, @PathVariable("tableId") Long tableId) throws IOException {
        byte[] data = unifiedGenService.downloadCode(tableId);
        genCode(response, data);
    }

    /**
     * 批量生成代码（下载方式）
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/batchDownload")
    public void batchDownload(HttpServletResponse response, String tables) throws IOException {
        String[] tableIds = StringUtils.split(tables, ",");
        Long[] ids = new Long[tableIds.length];
        for (int i = 0; i < tableIds.length; i++) {
            ids[i] = Long.parseLong(tableIds[i]);
        }
        byte[] data = unifiedGenService.downloadCodeBatch(ids);
        genCode(response, data);
    }

    /**
     * 生成代码（自定义路径）
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/genCode/{tableId}")
    public R<Void> genCode(@PathVariable("tableId") Long tableId) {
        unifiedGenService.generatorCode(tableId);
        return R.ok();
    }

    /**
     * 同步数据库
     */
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @GetMapping("/synchDb/{tableId}")
    public R<Void> synchDb(@PathVariable("tableId") Long tableId) {
        unifiedGenService.synchDb(tableId);
        return R.ok();
    }

    private void genCode(HttpServletResponse response, byte[] data) throws IOException {
        response.reset();
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition", "attachment; filename=\"ruoyi.zip\"");
        response.addHeader("Content-Length", "" + data.length);
        response.setContentType("application/octet-stream; charset=UTF-8");
        IoUtil.write(response.getOutputStream(), false, data);
    }

    /**
     * 批量生成代码
     *
     * @param tableIdStr 表ID串
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/batchGenCode")
    public void batchGenCode(HttpServletResponse response, String tableIdStr) throws IOException {
        String[] tableIds = Convert.toStrArray(tableIdStr);
        byte[] data = genTableService.downloadCode(tableIds);
        genCode(response, data);
    }

    /**
     * 查询数据源名称列表
     */
    @GetMapping(value = "/getDataNames")
    public R<Object> getCurrentDataSourceNameList() {
        return R.ok(DataBaseHelper.getDataSourceNameList());
    }

    /**
     * 根据表名查询数据表字段信息
     *
     * @param tableName 表名
     */
    @GetMapping(value = "/columns/{tableName}")
    public List<GenTableColumn> getTableColumns(@PathVariable("tableName") String tableName) {
        return genTableService.getTableColumns(tableName);
    }

    /**
     * 修改代码生成业务
     *
     * @param tableId 表ID
     */
    @RepeatSubmit()
    @GetMapping(value = "/{tableId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        List<GenTable> tables = genTableService.selectGenTableAll();
        List<GenTableColumn> list = genTableService.selectGenTableColumnListByTableId(tableId);
        Map<String, Object> map = new HashMap<>(3);
        map.put("info", table);
        map.put("rows", list);
        map.put("tables", tables);
        return R.ok(map);
    }

    // ==================== Online设计相关（新增功能） ====================

    /**
     * 查询设计列表
     */
    @GetMapping("/design/list")
    public R<List<GenTableDesign>> listDesigns(@RequestParam(required = false) String keyword) {
        List<GenTableDesign> list = unifiedGenService.listDesigns(keyword);
        return R.ok(list);
    }

    /**
     * 获取设计详情
     */
    @GetMapping("/design/{designId}")
    public R<GenTableDesign> getDesign(@PathVariable Long designId) {
        GenTableDesign design = unifiedGenService.getDesignById(designId);
        return R.ok(design);
    }

    /**
     * 保存设计
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.INSERT)
    @PostMapping("/design")
    public R<Long> saveDesign(@RequestBody GenTableDesign design) {
        Long designId = unifiedGenService.saveOrUpdateDesign(design);
        return R.ok(designId);
    }

    /**
     * 更新设计
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.UPDATE)
    @PutMapping("/design")
    public R<Long> updateDesign(@RequestBody GenTableDesign design) {
        Long designId = unifiedGenService.saveOrUpdateDesign(design);
        return R.ok(designId);
    }

    /**
     * 删除设计
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.DELETE)
    @DeleteMapping("/design/{designId}")
    public R<Void> removeDesign(@PathVariable Long designId) {
        unifiedGenService.removeDesignById(designId);
        return R.ok();
    }

    /**
     * 生成建表SQL
     */
    @PostMapping("/design/generateSql")
    public R<String> generateSql(@RequestBody Map<String, String> params) {
        String designJson = params.get("designJson");
        if (StringUtils.isBlank(designJson)) {
            throw new ServiceException("设计内容不能为空");
        }
        String sql = unifiedGenService.generateCreateTableSql(designJson);
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把 SQL 误塞进 msg、data 为 null
        return R.ok("查询成功", sql);
    }

    /**
     * 执行建表SQL
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.INSERT)
    @PostMapping("/design/createTable")
    public R<Void> createTable(@RequestBody Map<String, Object> params) {
        String sql = (String) params.get("sql");
        Boolean overwrite = (Boolean) params.getOrDefault("overwriteIfExists", false);
        if (StringUtils.isBlank(sql)) {
            throw new ServiceException("SQL不能为空");
        }
        unifiedGenService.executeCreateTableSql(sql, overwrite);
        return R.ok();
    }

    /**
     * 从设计导入到代码生成
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.IMPORT)
    @PostMapping("/design/importToGen")
    public R<Long> importFromDesign(@RequestBody Map<String, Object> params) {
        Long designId = Long.valueOf(params.get("designId").toString());
        String dataName = (String) params.getOrDefault("dataName", "master");
        Boolean overwrite = (Boolean) params.getOrDefault("overwriteIfExists", false);

        Long tableId = unifiedGenService.importFromDesign(designId, dataName, overwrite);
        return R.ok(tableId);
    }

    // ==================== Online表单数据操作 ====================

    /**
     * 获取Online表单配置
     */
    @GetMapping("/online/form/{tableId}")
    public R<Map<String, Object>> getOnlineForm(@PathVariable Long tableId) {
        Map<String, Object> form = unifiedGenService.getOnlineForm(tableId);
        return R.ok(form);
    }

    /**
     * 分页查询Online表单数据
     */
    @GetMapping("/online/data/{tableId}")
    public TableDataInfo<Map<String, Object>> listOnlineData(@PathVariable Long tableId, PageQuery pageQuery) {
        return unifiedGenService.listOnlineData(tableId, pageQuery);
    }

    /**
     * 获取Online表单数据详情
     */
    @GetMapping("/online/data/{tableId}/{dataId}")
    public R<Map<String, Object>> getOnlineData(@PathVariable Long tableId, @PathVariable Long dataId) {
        Map<String, Object> data = unifiedGenService.getOnlineDataById(tableId, dataId);
        return R.ok(data);
    }

    /**
     * 新增或修改Online表单数据
     */
    @Log(title = "Online表单数据", businessType = BusinessType.INSERT)
    @PostMapping("/online/data/{tableId}")
    public R<Integer> addOrUpdateOnlineData(@PathVariable Long tableId, @RequestBody Map<String, Object> data) {
        int result = unifiedGenService.addOrUpdateOnlineData(tableId, data);
        return R.ok(result);
    }

    /**
     * 删除Online表单数据
     */
    @Log(title = "Online表单数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/online/data/{tableId}")
    public R<Void> deleteOnlineData(@PathVariable Long tableId, @RequestParam List<Long> ids) {
        unifiedGenService.deleteOnlineData(tableId, ids);
        return R.ok();
    }

    /**
     * 通过表名新增或修改Online表单数据（供Flowable调用）
     */
    @Log(title = "Online表单数据", businessType = BusinessType.INSERT)
    @PostMapping("/online/dataByName/{tableName}")
    public R<Integer> addOrUpdateOnlineDataByName(@PathVariable String tableName, @RequestBody Map<String, Object> data) {
        int result = unifiedGenService.addOrUpdateOnlineDataByName(tableName, data);
        return R.ok(result);
    }

    /**
     * 查询Popup数据列表（带权限校验）
     */
    @GetMapping("/online/popupData/{tableId}/{dictTableName}")
    public TableDataInfo<Map<String, Object>> listPopupTableData(
            @PathVariable Long tableId,
            @PathVariable String dictTableName,
            PageQuery pageQuery) {
        return unifiedGenService.listPopupTableData(tableId, dictTableName, pageQuery);
    }

    /**
     * 查询Popup详情（带权限校验）
     */
    @GetMapping("/online/popupDetail/{tableId}/{dictTableName}/{id}")
    public R<Map<String, Object>> getPopupDetail(
            @PathVariable Long tableId,
            @PathVariable String dictTableName,
            @PathVariable String id) {
        Map<String, Object> data = unifiedGenService.getPopupDetail(tableId, dictTableName, id);
        return R.ok(data);
    }

    /**
     * 查询动态表数据（通用）
     */
    @GetMapping("/online/tableData/{tableName}")
    public TableDataInfo<Map<String, Object>> listTableData(
            @PathVariable String tableName,
            PageQuery pageQuery) {
        // 这里简化处理，实际应该通过某种方式获取tableId
        return unifiedGenService.listPopupTableData(null, tableName, pageQuery);
    }
}
