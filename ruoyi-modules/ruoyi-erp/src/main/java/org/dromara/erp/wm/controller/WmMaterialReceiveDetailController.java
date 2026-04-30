package org.dromara.erp.wm.controller;

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
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveDetailVo;
import org.dromara.erp.wm.service.IWmMaterialReceiveDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单明细Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/detail")
public class WmMaterialReceiveDetailController extends BaseController {

    private final IWmMaterialReceiveDetailService wmMaterialReceiveDetailService;

    /**
     * 查询物料入库单明细列表
     */
    @SaCheckPermission("wm:detail:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveDetailVo> list(WmMaterialReceiveDetailBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveDetailService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单明细列表
     */
    @SaCheckPermission("wm:detail:export")
    @Log(title = "物料入库单明细", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveDetailBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveDetailVo> list = wmMaterialReceiveDetailService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单明细数据", WmMaterialReceiveDetailVo.class, response);
    }

    /**
     * 获取物料入库单明细详细信息
     */
    @SaCheckPermission("wm:detail:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveDetailService.selectById(id));
    }

    /**
     * 新增物料入库单明细
     */
    @SaCheckPermission("wm:detail:add")
    @Log(title = "物料入库单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveDetailBo bo) {
        return toAjax(wmMaterialReceiveDetailService.insertByBo(bo));
    }

    /**
     * 修改物料入库单明细
     */
    @SaCheckPermission("wm:detail:edit")
    @Log(title = "物料入库单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveDetailBo bo) {
        return toAjax(wmMaterialReceiveDetailService.updateByBo(bo));
    }

    /**
     * 删除物料入库单明细
     */
    @SaCheckPermission("wm:detail:remove")
    @Log(title = "物料入库单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveDetailService.deleteByIds(ids));
    }
}
