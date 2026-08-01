package com.nexus.generator.controller;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
     * 分页查询已导入的代码生成业务列表。
     *
     * @param genTable  查询条件（表名/表描述等）
     * @param pageQuery 分页参数
     * @return 业务表分页结果
     */
    @GetMapping("/list")
    public TableDataInfo<GenTable> genList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageGenTableList(genTable, pageQuery);
    }

    /**
     * 分页查询数据源中可导入的数据库表列表。
     *
     * @param genTable  查询条件（表名/表描述等）
     * @param pageQuery 分页参数
     * @return 数据库表分页结果
     */
    @GetMapping("/db/list")
    public TableDataInfo<GenTable> dataList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageDbTableList(genTable, pageQuery);
    }

    /**
     * 查询指定业务表的字段列表与表基本信息。
     *
     * @param tableId 业务表ID
     * @return 包含字段列表（rows）与表信息（info）的映射
     */
    @GetMapping(value = "/column/{tableId}")
    public R<Map<String, Object>> columnList(@PathVariable("tableId") Long tableId) {
        Map<String, Object> map = new HashMap<>();
        map.put("rows", genTableService.selectGenTableColumnListByTableId(tableId));
        map.put("info", genTableService.selectGenTableById(tableId));
        return R.ok(map);
    }

    /**
     * 根据表名从数据源导入表结构并保存为代码生成配置。
     *
     * @param tables   逗号分隔的表名
     * @param dataName 数据源名称
     * @return 操作结果
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
     * 校验并更新已导入的代码生成业务配置。
     *
     * @param genTable 代码生成业务对象
     * @return 操作结果
     */
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> editSave(@RequestBody GenTable genTable) {
        genTableService.validateEdit(genTable);
        genTableService.updateGenTable(genTable);
        return R.ok();
    }

    /**
     * 批量删除代码生成业务配置及其字段配置。
     *
     * @param tableIds 业务表ID数组
     * @return 操作结果
     */
    @Log(title = "代码生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tableIds}")
    public R<Void> remove(@PathVariable Long[] tableIds) {
        genTableService.deleteGenTableByIds(tableIds);
        return R.ok();
    }

    /**
     * 预览指定业务表将要生成的各文件代码。
     *
     * @param tableId 业务表ID
     * @return 模板路径与生成内容映射
     */
    @GetMapping("/preview/{tableId}")
    public R<Map<String, String>> preview(@PathVariable("tableId") Long tableId) {
        Map<String, String> dataMap = unifiedGenService.previewCode(tableId);
        return R.ok(dataMap);
    }

    /**
     * 生成代码并以 zip 压缩包形式下载。
     *
     * @param response HTTP 响应
     * @param tableId  业务表ID
     * @throws IOException IO 异常
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/download/{tableId}")
    public void download(HttpServletResponse response, @PathVariable("tableId") Long tableId) throws IOException {
        byte[] data = unifiedGenService.downloadCode(tableId);
        genCode(response, data);
    }

    /**
     * 批量生成多张表的代码并以 zip 压缩包形式下载。
     *
     * @param response HTTP 响应
     * @param tables   逗号分隔的表ID串
     * @throws IOException IO 异常
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
     * 将代码生成到配置指定的自定义路径（不打包下载）。
     *
     * @param tableId 业务表ID
     * @return 操作结果
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/genCode/{tableId}")
    public R<Void> genCode(@PathVariable("tableId") Long tableId) {
        unifiedGenService.generatorCode(tableId);
        return R.ok();
    }

    /**
     * 将业务表同步到最新数据库表结构（新增/更新/删除字段）。
     *
     * @param tableId 业务表ID
     * @return 操作结果
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
     * 批量生成多张表的代码并以 zip 压缩包形式下载。
     *
     * @param response    HTTP 响应
     * @param tableIdStr  逗号分隔的表ID串
     * @throws IOException IO 异常
     */
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/batchGenCode")
    public void batchGenCode(HttpServletResponse response, String tableIdStr) throws IOException {
        String[] tableIds = Convert.toStrArray(tableIdStr);
        byte[] data = genTableService.downloadCode(tableIds);
        genCode(response, data);
    }

    /**
     * 查询当前系统可用的数据源名称列表。
     *
     * @return 数据源名称列表
     */
    @GetMapping(value = "/getDataNames")
    public R<Object> getCurrentDataSourceNameList() {
        return R.ok(DataBaseHelper.getDataSourceNameList());
    }

    /**
     * 根据表名查询对应的代码生成字段配置列表。
     *
     * @param tableName 表名
     * @return 字段配置列表
     */
    @GetMapping(value = "/columns/{tableName}")
    public List<GenTableColumn> getTableColumns(@PathVariable("tableName") String tableName) {
        return genTableService.getTableColumns(tableName);
    }

    /**
     * 获取指定业务表的详情（表信息、字段列表及全部表下拉）。
     *
     * @param tableId 业务表ID
     * @return 包含 info/rows/tables 的映射
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
     * 按关键字查询 Online 表设计列表。
     *
     * @param keyword 关键字（匹配设计名/表名/表描述）
     * @return 设计列表
     */
    @GetMapping("/design/list")
    public R<List<GenTableDesign>> listDesigns(@RequestParam(required = false) String keyword) {
        List<GenTableDesign> list = unifiedGenService.listDesigns(keyword);
        return R.ok(list);
    }

    /**
     * 获取指定 Online 表设计详情。
     *
     * @param designId 设计ID
     * @return 设计详情
     */
    @GetMapping("/design/{designId}")
    public R<GenTableDesign> getDesign(@PathVariable Long designId) {
        GenTableDesign design = unifiedGenService.getDesignById(designId);
        return R.ok(design);
    }

    /**
     * 新增 Online 表设计。
     *
     * @param design 设计对象
     * @return 新建的设计ID
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.INSERT)
    @PostMapping("/design")
    public R<Long> saveDesign(@RequestBody GenTableDesign design) {
        Long designId = unifiedGenService.saveOrUpdateDesign(design);
        return R.ok(designId);
    }

    /**
     * 更新已有的 Online 表设计。
     *
     * @param design 设计对象（含 designId）
     * @return 更新的设计ID
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.UPDATE)
    @PutMapping("/design")
    public R<Long> updateDesign(@RequestBody GenTableDesign design) {
        Long designId = unifiedGenService.saveOrUpdateDesign(design);
        return R.ok(designId);
    }

    /**
     * 删除指定 Online 表设计。
     *
     * @param designId 设计ID
     * @return 操作结果
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.DELETE)
    @DeleteMapping("/design/{designId}")
    public R<Void> removeDesign(@PathVariable Long designId) {
        unifiedGenService.removeDesignById(designId);
        return R.ok();
    }

    /**
     * 根据设计 JSON 生成建表及外键 SQL。
     *
     * @param params 包含 designJson 的请求参数
     * @return 生成的建表 SQL
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
     * 在指定数据源执行建表 SQL（可选覆盖已存在表）。
     *
     * @param params 包含 sql 与 overwriteIfExists 的请求参数
     * @return 操作结果
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.INSERT)
    @SaCheckPermission("tool:gen:design")
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
     * 将 Online 设计转换为代码生成配置并导入。
     *
     * @param params 包含 designId、dataName、overwriteIfExists 的请求参数
     * @return 生成的代码生成表ID
     */
    @Log(title = "代码生成-设计", businessType = BusinessType.IMPORT)
    @SaCheckPermission("tool:gen:design")
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
     * 获取指定表对应的 Online 表单配置（含字段与布局）。
     *
     * @param tableId 业务表ID
     * @return 表单配置映射
     */
    @GetMapping("/online/form/{tableId}")
    public R<Map<String, Object>> getOnlineForm(@PathVariable Long tableId) {
        Map<String, Object> form = unifiedGenService.getOnlineForm(tableId);
        return R.ok(form);
    }

    /**
     * 分页查询 Online 表单对应的业务表数据。
     *
     * @param tableId   业务表ID
     * @param pageQuery 分页参数
     * @return 业务数据分页结果（含表单配置 map）
     */
    @GetMapping("/online/data/{tableId}")
    public TableDataInfo<Map<String, Object>> listOnlineData(@PathVariable Long tableId, PageQuery pageQuery) {
        return unifiedGenService.listOnlineData(tableId, pageQuery);
    }

    /**
     * 根据主键查询 Online 表单单条业务数据。
     *
     * @param tableId 业务表ID
     * @param dataId  数据ID
     * @return 数据详情映射
     */
    @GetMapping("/online/data/{tableId}/{dataId}")
    public R<Map<String, Object>> getOnlineData(@PathVariable Long tableId, @PathVariable Long dataId) {
        Map<String, Object> data = unifiedGenService.getOnlineDataById(tableId, dataId);
        return R.ok(data);
    }

    /**
     * 新增或更新 Online 表单业务数据（按主键是否存在自动判断）。
     *
     * @param tableId 业务表ID
     * @param data    业务数据
     * @return 影响行数
     */
    @Log(title = "Online表单数据", businessType = BusinessType.INSERT)
    @PostMapping("/online/data/{tableId}")
    public R<Integer> addOrUpdateOnlineData(@PathVariable Long tableId, @RequestBody Map<String, Object> data) {
        int result = unifiedGenService.addOrUpdateOnlineData(tableId, data);
        return R.ok(result);
    }

    /**
     * 批量删除 Online 表单业务数据。
     *
     * @param tableId 业务表ID
     * @param ids     数据ID列表
     * @return 操作结果
     */
    @Log(title = "Online表单数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/online/data/{tableId}")
    public R<Void> deleteOnlineData(@PathVariable Long tableId, @RequestParam List<Long> ids) {
        unifiedGenService.deleteOnlineData(tableId, ids);
        return R.ok();
    }

    /**
     * 通过表名新增或更新 Online 表单数据，供 Flowable 流程调用。
     *
     * @param tableName 表名
     * @param data      业务数据
     * @return 影响行数
     */
    @Log(title = "Online表单数据", businessType = BusinessType.INSERT)
    @PostMapping("/online/dataByName/{tableName}")
    public R<Integer> addOrUpdateOnlineDataByName(@PathVariable String tableName, @RequestBody Map<String, Object> data) {
        int result = unifiedGenService.addOrUpdateOnlineDataByName(tableName, data);
        return R.ok(result);
    }

    /**
     * 分页查询 Popup 弹窗来源表数据（带表名授权校验）。
     *
     * @param tableId       业务表ID（用于权限上下文）
     * @param dictTableName Popup 来源表名
     * @param pageQuery     分页参数
     * @return 来源表数据分页结果
     */
    @GetMapping("/online/popupData/{tableId}/{dictTableName}")
    public TableDataInfo<Map<String, Object>> listPopupTableData(
            @PathVariable Long tableId,
            @PathVariable String dictTableName,
            PageQuery pageQuery) {
        return unifiedGenService.listPopupTableData(tableId, dictTableName, pageQuery);
    }

    /**
     * 查询 Popup 弹窗来源表单条数据（带表名授权校验）。
     *
     * @param tableId       业务表ID（用于权限上下文）
     * @param dictTableName Popup 来源表名
     * @param id            数据ID
     * @return 数据详情映射
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
     * 通用动态表分页查询（按表名），用于未携带 tableId 的场景。
     *
     * @param tableName 表名
     * @param pageQuery 分页参数
     * @return 动态表数据分页结果
     */
    @GetMapping("/online/tableData/{tableName}")
    public TableDataInfo<Map<String, Object>> listTableData(
            @PathVariable String tableName,
            PageQuery pageQuery) {
        // 这里简化处理，实际应该通过某种方式获取tableId
        return unifiedGenService.listPopupTableData(null, tableName, pageQuery);
    }
}
