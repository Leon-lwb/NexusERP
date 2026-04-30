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
import org.dromara.erp.wm.domain.bo.WmStorageLocationBo;
import org.dromara.erp.wm.domain.vo.WmStorageLocationVo;
import org.dromara.erp.wm.service.IWmStorageLocationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库位Controller
 *
 * @author ruoyi
 * @date 2025-12-02
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/storagelocation")
public class WmStorageLocationController extends BaseController {

    private final IWmStorageLocationService wmStorageLocationService;

    /**
     * 查询库位列表
     */
    @SaCheckPermission("wm:storagelocation:list")
    @GetMapping("/list")
    public TableDataInfo<WmStorageLocationVo> list(WmStorageLocationBo bo, PageQuery pageQuery) {
        return wmStorageLocationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库位列表
     */
    @SaCheckPermission("wm:storagelocation:export")
    @Log(title = "库位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStorageLocationBo bo, HttpServletResponse response) {
        List<WmStorageLocationVo> list = wmStorageLocationService.selectList(bo);
        ExcelUtil.exportExcel(list, "库位数据", WmStorageLocationVo.class, response);
    }

    /**
     * 获取库位详细信息
     */
    @SaCheckPermission("wm:storagelocation:query")
    @GetMapping("/{id}")
    public R<WmStorageLocationVo> getInfo(@PathVariable Long id) {
        return R.ok(wmStorageLocationService.selectById(id));
    }

    /**
     * 新增库位
     */
    @SaCheckPermission("wm:storagelocation:add")
    @Log(title = "库位", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStorageLocationBo bo) {
        return toAjax(wmStorageLocationService.insertByBo(bo));
    }

    /**
     * 修改库位
     */
    @SaCheckPermission("wm:storagelocation:edit")
    @Log(title = "库位", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStorageLocationBo bo) {
        return toAjax(wmStorageLocationService.updateByBo(bo));
    }

    /**
     * 删除库位
     */
    @SaCheckPermission("wm:storagelocation:remove")
    @Log(title = "库位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmStorageLocationService.deleteByIds(ids));
    }
}
