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
import org.dromara.erp.wm.domain.bo.WmStorageAreaBo;
import org.dromara.erp.wm.domain.vo.WmStorageAreaVo;
import org.dromara.erp.wm.service.IWmStorageAreaService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库区Controller
 *
 * @author ruoyi
 * @date 2025-12-01
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/storagearea")
public class WmStorageAreaController extends BaseController {

    private final IWmStorageAreaService wmStorageAreaService;

    /**
     * 查询库区列表
     */
    @SaCheckPermission("wm:storagearea:list")
    @GetMapping("/list")
    public TableDataInfo<WmStorageAreaVo> list(WmStorageAreaBo bo, PageQuery pageQuery) {
        return wmStorageAreaService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库区列表
     */
    @SaCheckPermission("wm:storagearea:export")
    @Log(title = "库区", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStorageAreaBo bo, HttpServletResponse response) {
        List<WmStorageAreaVo> list = wmStorageAreaService.selectList(bo);
        ExcelUtil.exportExcel(list, "库区数据", WmStorageAreaVo.class, response);
    }

    /**
     * 获取库区详细信息
     */
    @SaCheckPermission("wm:storagearea:query")
    @GetMapping("/{id}")
    public R<WmStorageAreaVo> getInfo(@PathVariable Long id) {
        return R.ok(wmStorageAreaService.selectById(id));
    }

    /**
     * 新增库区
     */
    @SaCheckPermission("wm:storagearea:add")
    @Log(title = "库区", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStorageAreaBo bo) {
        return toAjax(wmStorageAreaService.insertByBo(bo));
    }

    /**
     * 修改库区
     */
    @SaCheckPermission("wm:storagearea:edit")
    @Log(title = "库区", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStorageAreaBo bo) {
        return toAjax(wmStorageAreaService.updateByBo(bo));
    }

    /**
     * 删除库区
     */
    @SaCheckPermission("wm:storagearea:remove")
    @Log(title = "库区", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmStorageAreaService.deleteByIds(ids));
    }
}
