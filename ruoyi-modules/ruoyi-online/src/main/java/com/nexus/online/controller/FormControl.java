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
 * online表单功能
 *
 * @author wanghuan
 */

@RequiredArgsConstructor
@RestController
@RequestMapping("/online")
public class FormControl extends BaseController {

	private final IFormService formService;

	/**
     * 查询online列表
     */
    @GetMapping("/list")
    public TableDataInfo<GenTable> onlineList(GenTable genTable, PageQuery pageQuery) {
        return formService.selectPageGenTableList(genTable, pageQuery);
    }

	/**
     * 查询online列表
     *
     * @param tableId 表tableId
     */
    @GetMapping("/{tableId}")
    public R<?> getOnlineForm(@PathVariable("tableId")  Long tableId) {
    	return R.ok(formService.getOnlineForm(tableId));
    }

    /**
     * 查询数据源名称列表
     */
    @GetMapping(value = "/getDataNames")
    public R<Object> getCurrentDataSourceNameList(){
        return R.ok(DataBaseHelper.getDataSourceNameList());
    }

    /**
     * 查询online表数据
     *
     * @param tableId 表id, tableName 表名
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
     * 查询online字典表数据
     *
     * @param tableName 表名
     */
    @GetMapping("/getTableData/{tableName}")
    public TableDataInfo<Map> getDictTableData(@PathVariable("tableName")  String tableName, PageQuery pageQuery) {
    	return formService.listTableData(tableName, pageQuery);
    }

    /**
     * 更新online表数据
     *
     * @param tableId 表id, tableName 表名
     */
    @PostMapping("/addOrUpdateData/{tableId}/{tableName}")
    public R<Object> addOrUpdateOnlineData(@PathVariable("tableId")  Long tableId,
    		          @PathVariable("tableName")  String tableName, @RequestBody Map<String, Object> map) {
    	return R.ok(formService.addOrUpdateOnlineData(tableId, tableName, map));
    }

    /**
     * 更新online表数据
     *
     * @param  tableName 表名
     */
    @PostMapping("/addOrUpdateDataByName/{tableName}")
    public R<Object> addOrUpdateOnlineDataByName(@PathVariable("tableName")  String tableName, @RequestBody Map<String, Object> map) {
        return R.ok(formService.addOrUpdateOnlineDataByName(tableName, map));
    }

    /**
     * 删除online表数据
     *
     * @param tableId 表id, tableName 表名 , ids 删除的id
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
     * 查询online的id详细数据
     *
     * @param tableId 表id, tableName 表名, id 查询的id
     */
    @PostMapping("/getOnlineData/{tableId}/{tableName}")
    public R<Object> getOnlineData(@PathVariable("tableId")  Long tableId,
    		          @PathVariable("tableName")  String tableName, @RequestBody Long id) {
    	return R.ok(formService.getOnlineData(tableId, tableName, id));
    }

    /**
     * 弹窗详情数据（新接口）
     */
    @GetMapping("/detail/{tableId}/{tableName}/{id}")
    public R<Map<String, Object>> getPopupDetail(
        @PathVariable("tableId") Long tableId,
        @PathVariable("tableName") String tableName,
        @PathVariable("id") String id) {

        return R.ok(formService.getPopupDetail(tableId, tableName, id));
    }

}
