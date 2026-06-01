package com.nexus.erp.wm.controller;

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
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import com.nexus.erp.wm.service.IWmMaterialReceiveDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单明细 Controller
 * <p>
 * 管理物料入库单的明细数据，记录每一笔入库物料的详细信息。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/detail")
public class WmMaterialReceiveDetailController extends BaseController {

    private final IWmMaterialReceiveDetailService wmMaterialReceiveDetailService;

    /**
     * 分页查询物料入库单明细列表
     * <p>
     * 权限标识：{@code wm:detail:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料入库单明细分页列表
     */
    @SaCheckPermission("wm:detail:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveDetailVo> list(WmMaterialReceiveDetailBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveDetailService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单明细数据到 Excel
     * <p>权限标识：{@code wm:detail:export}</p>
     * <p>日志记录：title="物料入库单明细"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:detail:export")
    @Log(title = "物料入库单明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveDetailBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveDetailVo> list = wmMaterialReceiveDetailService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单明细数据", WmMaterialReceiveDetailVo.class, response);
    }

    /**
     * 根据主键获取物料入库单明细详细信息
     * <p>
     * 权限标识：{@code wm:detail:query}</p>
     *
     * @param id 入库单明细 ID
     * @return 入库单明细详细信息
     */
    @SaCheckPermission("wm:detail:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveDetailService.selectById(id));
    }

    /**
     * 新增物料入库单明细
     * <p>
     * 权限标识：{@code wm:detail:add}</p>
     * <p>日志记录：title="物料入库单明细"，businessType=INSERT</p>
     *
     * @param bo 入库单明细业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:detail:add")
    @Log(title = "物料入库单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveDetailBo bo) {
        return toAjax(wmMaterialReceiveDetailService.insertByBo(bo));
    }

    /**
     * 修改物料入库单明细
     * <p>
     * 权限标识：{@code wm:detail:edit}</p>
     * <p>日志记录：title="物料入库单明细"，businessType=UPDATE</p>
     *
     * @param bo 入库单明细业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:detail:edit")
    @Log(title = "物料入库单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveDetailBo bo) {
        return toAjax(wmMaterialReceiveDetailService.updateByBo(bo));
    }

    /**
     * 批量删除物料入库单明细
     * <p>
     * 权限标识：{@code wm:detail:remove}</p>
     * <p>日志记录：title="物料入库单明细"，businessType=DELETE</p>
     *
     * @param ids 要删除的入库单明细 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:detail:remove")
    @Log(title = "物料入库单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveDetailService.deleteByIds(ids));
    }
}
