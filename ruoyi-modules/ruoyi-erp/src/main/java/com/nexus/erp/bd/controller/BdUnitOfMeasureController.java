package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdUnitOfMeasureBo;
import com.nexus.erp.bd.domain.vo.BdUnitOfMeasureVo;
import com.nexus.erp.bd.service.IBdUnitOfMeasureService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计量单位Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/measure")
public class BdUnitOfMeasureController extends BaseController {

    private final IBdUnitOfMeasureService bdUnitOfMeasureService;

    /**
     * 分页查询计量单位列表
     * <p>权限标识：{@code bd:measure:list}</p>
     *
     * @param bo        查询参数，包含计量单位名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 计量单位分页列表
     */
    @SaCheckPermission("bd:measure:list")
    @GetMapping("/list")
    public TableDataInfo<BdUnitOfMeasureVo> list(BdUnitOfMeasureBo bo, PageQuery pageQuery) {
        return bdUnitOfMeasureService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询所有计量单位列表（无分页）
     * <p>权限标识：{@code bd:measure:list}</p>
     *
     * @return 全部计量单位列表
     */
    @SaCheckPermission("bd:measure:list")
    @GetMapping("/all")
    public R<List<BdUnitOfMeasureVo>> all() {
        return R.ok(bdUnitOfMeasureService.selectList(new BdUnitOfMeasureBo()));
    }

    /**
     * 导出计量单位数据到 Excel
     * <p>权限标识：{@code bd:measure:export}</p>
     * <p>日志记录：title="计量单位"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("bd:measure:export")
    @Log(title = "计量单位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdUnitOfMeasureBo bo, HttpServletResponse response) {
        List<BdUnitOfMeasureVo> list = bdUnitOfMeasureService.selectList(bo);
        ExcelUtil.exportExcel(list, "计量单位数据", BdUnitOfMeasureVo.class, response);
    }

    /**
     * 获取计量单位详细信息
     * <p>权限标识：{@code bd:measure:query}</p>
     *
     * @param uomId 计量单位ID
     * @return 计量单位详细信息
     */
    @SaCheckPermission("bd:measure:query")
    @GetMapping("/{uomId}")
    public R<BdUnitOfMeasureVo> getInfo(@PathVariable Long uomId) {
        return R.ok(bdUnitOfMeasureService.selectById(uomId));
    }

    /**
     * 根据主计量单位 ID 查询其所有换算单位
     * <p>权限标识：{@code bd:measure:query}</p>
     *
     * @param primaryId 主计量单位 ID
     * @return 换算单位列表
     */
    @SaCheckPermission("bd:measure:query")
    @GetMapping("/byPrimary/{primaryId}")
    public R<List<BdUnitOfMeasureVo>> getByPrimaryId(@PathVariable Long primaryId) {
        return R.ok(bdUnitOfMeasureService.selectByPrimaryId(primaryId));
    }

    /**
     * 查询所有计量单位类别列表
     * <p>权限标识：{@code bd:measure:list}</p>
     *
     * @return 计量单位类别名称列表
     */
    @SaCheckPermission("bd:measure:list")
    @GetMapping("/categories")
    public R<List<String>> getCategories() {
        return R.ok(bdUnitOfMeasureService.selectCategories());
    }

    /**
     * 新增计量单位
     * <p>权限标识：{@code bd:measure:add}</p>
     * <p>日志记录：title="计量单位"，businessType=INSERT</p>
     *
     * @param bo 计量单位业务对象，包含计量单位名称、编码及换算关系等字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:measure:add")
    @Log(title = "计量单位", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdUnitOfMeasureBo bo) {
        return toAjax(bdUnitOfMeasureService.insertByBo(bo));
    }

    /**
     * 修改计量单位
     * <p>权限标识：{@code bd:measure:edit}</p>
     * <p>日志记录：title="计量单位"，businessType=UPDATE</p>
     *
     * @param bo 计量单位业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:measure:edit")
    @Log(title = "计量单位", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdUnitOfMeasureBo bo) {
        return toAjax(bdUnitOfMeasureService.updateByBo(bo));
    }

    /**
     * 批量删除计量单位
     * <p>权限标识：{@code bd:measure:remove}</p>
     * <p>日志记录：title="计量单位"，businessType=DELETE</p>
     *
     * @param uomIds 计量单位 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:measure:remove")
    @Log(title = "计量单位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{uomIds}")
    public R<Void> remove(@PathVariable Long[] uomIds) {
        return toAjax(bdUnitOfMeasureService.deleteByIds(uomIds));
    }
}
