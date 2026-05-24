package org.dromara.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.bd.domain.bo.BdBomDetailBo;
import org.dromara.erp.bd.domain.vo.BdBomDetailVo;
import org.dromara.erp.bd.service.IBdBomDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BOM明细Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/bom/detail")
public class BdBomDetailController extends BaseController {

    private final IBdBomDetailService bdBomDetailService;

    /**
     * 分页查询 BOM 明细列表
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param bo        查询参数，包含 BOM 主表 ID 等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return BOM 明细分页列表
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/list")
    public TableDataInfo<BdBomDetailVo> list(BdBomDetailBo bo, PageQuery pageQuery) {
        return bdBomDetailService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出BOM明细数据到 Excel
     * <p>权限标识：{@code bd:bom:export}</p>
     * <p>日志记录：title="BOM明细"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:bom:export")
    @Log(title = "BOM明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdBomDetailBo bo, HttpServletResponse response) {
        List<BdBomDetailVo> list = bdBomDetailService.selectList(bo);
        ExcelUtil.exportExcel(list, "BOM明细数据", BdBomDetailVo.class, response);
    }

    /**
     * 根据主键获取BOM明细详细信息
     * <p>权限标识：{@code bd:bom:query}</p>
     *
     * @param id BOM明细 ID
     * @return BOM明细详细信息
     */
    @SaCheckPermission("bd:bom:query")
    @GetMapping("/{id}")
    public R<BdBomDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(bdBomDetailService.selectById(id));
    }

    /**
     * 新增BOM明细
     * <p>权限标识：{@code bd:bom:add}</p>
     * <p>日志记录：title="BOM明细"，businessType=INSERT</p>
     *
     * @param bo BOM明细业务对象，包含物料、数量和工序等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdBomDetailBo bo) {
        return toAjax(bdBomDetailService.insertByBo(bo));
    }

    /**
     * 修改BOM明细
     * <p>权限标识：{@code bd:bom:edit}</p>
     * <p>日志记录：title="BOM明细"，businessType=UPDATE</p>
     *
     * @param bo BOM明细业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:edit")
    @Log(title = "BOM明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdBomDetailBo bo) {
        return toAjax(bdBomDetailService.updateByBo(bo));
    }

    /**
     * 批量删除BOM明细
     * <p>权限标识：{@code bd:bom:remove}</p>
     * <p>日志记录：title="BOM明细"，businessType=DELETE</p>
     *
     * @param ids BOM明细 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:remove")
    @Log(title = "BOM明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdBomDetailService.deleteByIds(ids));
    }

    /**
     * 批量新增 BOM 明细
     * <p>
     * 一次性批量添加多条 BOM 明细记录，适用于从 Excel 导入或批量录入场景。</p>
     * <p>权限标识：{@code bd:bom:add}</p>
     * <p>日志记录：title="BOM明细"，businessType=INSERT</p>
     *
     * @param list BOM 明细业务对象列表
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:bom:add")
    @Log(title = "BOM明细", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public R<Void> saveBatch(@RequestBody List<BdBomDetailBo> list) {
        return toAjax(bdBomDetailService.saveBatch(list));
    }
}
