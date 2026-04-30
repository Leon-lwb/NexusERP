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
import org.dromara.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import org.dromara.erp.wm.domain.vo.WmMaterialReceiveLineVo;
import org.dromara.erp.wm.service.IWmMaterialReceiveLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料入库单行Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/receiveline")
public class WmMaterialReceiveLineController extends BaseController {

    private final IWmMaterialReceiveLineService wmMaterialReceiveLineService;

    /**
     * 查询物料入库单行列表
     */
    @SaCheckPermission("wm:receiveLine:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialReceiveLineVo> list(WmMaterialReceiveLineBo bo, PageQuery pageQuery) {
        return wmMaterialReceiveLineService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料入库单行列表
     */
    @SaCheckPermission("wm:receiveLine:export")
    @Log(title = "物料入库单行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialReceiveLineBo bo, HttpServletResponse response) {
        List<WmMaterialReceiveLineVo> list = wmMaterialReceiveLineService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料入库单行数据", WmMaterialReceiveLineVo.class, response);
    }

    /**
     * 获取物料入库单行详细信息
     */
    @SaCheckPermission("wm:receiveLine:query")
    @GetMapping("/{id}")
    public R<WmMaterialReceiveLineVo> getInfo(@PathVariable Long id) {
        return R.ok(wmMaterialReceiveLineService.selectById(id));
    }

    /**
     * 新增物料入库单行
     */
    @SaCheckPermission("wm:receiveLine:add")
    @Log(title = "物料入库单行", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialReceiveLineBo bo) {
        return toAjax(wmMaterialReceiveLineService.insertByBo(bo));
    }

    /**
     * 修改物料入库单行
     */
    @SaCheckPermission("wm:receiveLine:edit")
    @Log(title = "物料入库单行", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialReceiveLineBo bo) {
        return toAjax(wmMaterialReceiveLineService.updateByBo(bo));
    }

    /**
     * 删除物料入库单行
     */
    @SaCheckPermission("wm:receiveLine:remove")
    @Log(title = "物料入库单行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmMaterialReceiveLineService.deleteByIds(ids));
    }
}
