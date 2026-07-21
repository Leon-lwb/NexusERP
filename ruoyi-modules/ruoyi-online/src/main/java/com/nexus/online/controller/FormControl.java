package com.nexus.online.controller;

import java.util.List;
import java.util.Map;

import com.nexus.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.helper.DataBaseHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.generator.domain.GenTable;
import com.nexus.online.service.IFormService;

import lombok.RequiredArgsConstructor;

/**
 * 在线表单（online）功能 Controller
 * <p>提供在线表单的元数据查询、动态表数据增删改查、字典/弹窗来源表数据查询等 REST 接口，
 * 属于在线低代码表单构建器模块（ruoyi-online）。所有动态表访问均基于 tableId 进行表名与字段白名单校验。</p>
 *
 * @author wanghuan
 */

@RequiredArgsConstructor
@RestController
@RequestMapping("/online")
public class FormControl extends BaseController {

	/** 在线表单业务服务，提供元数据查询与动态表数据读写能力。 */
	private final IFormService formService;

	/**
     * 分页查询在线表单（代码生成表）列表。
     *
     * @param genTable 查询条件，可包含表名、表注释、数据源名及创建时间范围
     * @param pageQuery 分页参数，包含页码、每页大小及排序条件
     * @return 分页结果，包含符合条件的在线表单表配置列表
     */
    @GetMapping("/list")
    public TableDataInfo<GenTable> onlineList(GenTable genTable, PageQuery pageQuery) {
        return formService.selectPageGenTableList(genTable, pageQuery);
    }

	/**
     * 获取指定在线表单的设计元数据（含字段与子表配置）。
     *
     * @param tableId 在线表单对应的表 ID
     * @return 表单设计结构，区分 single / main-sub / sub 三种类型
     */
    @GetMapping("/{tableId}")
    public R<?> getOnlineForm(@PathVariable("tableId")  Long tableId) {
    	return R.ok(formService.getOnlineForm(tableId));
    }

    /**
     * 查询当前系统可访问的数据源名称列表。
     *
     * @return 数据源名称集合（用于在线表单选择存储位置）
     */
    @GetMapping(value = "/getDataNames")
    public R<Object> getCurrentDataSourceNameList(){
        return R.ok(DataBaseHelper.getDataSourceNameList());
    }

    /**
     * 分页查询在线表单表数据。
     *
     * @param tableId 在线表单对应的表 ID（用于表名授权校验）
     * @param tableName 实际数据表名
     * @param pageQuery 分页参数
     * @return 分页结果，包含表数据记录及表单字段元信息
     */
    @GetMapping("/getData/{tableId}/{tableName}")
    public TableDataInfo<Map> getOnlineData(@PathVariable("tableId")  Long tableId,
    		          @PathVariable("tableName")  String tableName, PageQuery pageQuery) {
    	return formService.listOnlineData(tableId, tableName, pageQuery);
    }

    /**
     * 查询 popup（字典表/关联表）数据：专用于弹窗选择，不再复用 /getData 语义
     *
     * @param tableId 当前 online 表的 tableId（作为授权上下文）
     * @param dictTableName 弹窗来源表（字段配置里的 dictType）
     */
    @GetMapping("/getPopupData/{tableId}/{dictTableName}")
    public TableDataInfo<Map> getPopupTableData(@PathVariable("tableId") Long tableId,
                                                @PathVariable("dictTableName") String dictTableName,
                                                PageQuery pageQuery) {
        return formService.listPopupTableData(tableId, dictTableName, pageQuery);
    }

    /**
     * 查询字典/关联表数据（按表名，无需 tableId 上下文）。
     *
     * @param tableName 目标字典或关联表名
     * @param pageQuery 分页参数
     * @return 分页结果，包含字典表数据记录
     */
    @GetMapping("/getTableData/{tableName}")
    public TableDataInfo<Map> getDictTableData(@PathVariable("tableName")  String tableName, PageQuery pageQuery) {
    	return formService.listTableData(tableName, pageQuery);
    }

    /**
     * 新增或更新在线表单表数据（支持单表与主子表结构）。
     *
     * @param tableId 在线表单对应的表 ID（用于表名授权校验）
     * @param tableName 实际数据表名
     * @param map 数据内容；主子表结构时含 main / sub 两部分
     * @return 操作结果，包含受影响记录状态
     */
    @PostMapping("/addOrUpdateData/{tableId}/{tableName}")
    public R<Object> addOrUpdateOnlineData(@PathVariable("tableId")  Long tableId,
    		          @PathVariable("tableName")  String tableName, @RequestBody Map<String, Object> map) {
    	return R.ok(formService.addOrUpdateOnlineData(tableId, tableName, map));
    }

    /**
     * 按表名新增或更新在线表单表数据（无需 tableId 上下文）。
     *
     * @param tableName 实际数据表名（需已纳入 online 配置白名单）
     * @param map 数据内容；主子表结构时含 main / sub 两部分
     * @return 操作结果，包含受影响记录状态
     */
    @PostMapping("/addOrUpdateDataByName/{tableName}")
    public R<Object> addOrUpdateOnlineDataByName(@PathVariable("tableName")  String tableName, @RequestBody Map<String, Object> map) {
        return R.ok(formService.addOrUpdateOnlineDataByName(tableName, map));
    }

    /**
     * 批量删除在线表单表数据（含关联子表数据）。
     *
     * @param tableId 在线表单对应的表 ID（用于表名授权校验）
     * @param tableName 实际数据表名
     * @param ids 待删除记录的主键 ID 列表
     * @return 操作结果，成功时返回成功状态
     */
    @DeleteMapping("/delOnlineData/{tableId}/{tableName}")
    public R<Void> delOnlineData(
        @PathVariable("tableId") Long tableId,
        @PathVariable("tableName") String tableName,
        @RequestBody List<Long> ids) {

        // 表名校验（示例校验）
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("非法表名");
        }

        formService.deleteOnlineData(tableId, tableName, ids);
        return R.ok("删除成功！");
    }

    /**
     * 查询在线表单指定记录的详细数据。
     *
     * @param tableId 在线表单对应的表 ID（用于表名授权校验）
     * @param tableName 实际数据表名
     * @param id 待查询记录的主键 ID
     * @return 记录详情，主子表结构时包含主表与子表数据
     */
    @PostMapping("/getOnlineData/{tableId}/{tableName}")
    public R<Object> getOnlineData(@PathVariable("tableId")  Long tableId,
    		          @PathVariable("tableName")  String tableName, @RequestBody Long id) {
    	return R.ok(formService.getOnlineData(tableId, tableName, id));
    }

    /**
     * 查询弹窗来源表（字典/关联表）指定记录的详情数据。
     *
     * @param tableId 当前 online 表的 ID（用于弹窗来源表授权校验）
     * @param tableName 弹窗来源表名
     * @param id 待查询记录的主键 ID
     * @return 弹窗来源表的记录详情
     */
    @GetMapping("/detail/{tableId}/{tableName}/{id}")
    public R<Map<String, Object>> getPopupDetail(
        @PathVariable("tableId") Long tableId,
        @PathVariable("tableName") String tableName,
        @PathVariable("id") String id) {

        return R.ok(formService.getPopupDetail(tableId, tableName, id));
    }

}
