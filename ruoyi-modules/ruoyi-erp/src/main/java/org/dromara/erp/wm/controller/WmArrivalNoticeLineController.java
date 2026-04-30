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
import org.dromara.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import org.dromara.erp.wm.domain.vo.WmArrivalNoticeLineVo;
import org.dromara.erp.wm.service.IWmArrivalNoticeLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 到货通知单行Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/arrivalnoticeline")
public class WmArrivalNoticeLineController extends BaseController {

    private final IWmArrivalNoticeLineService wmArrivalNoticeLineService;

    /**
     * 查询到货通知单行列表
     */
    @SaCheckPermission("wm:arrivalNoticeLine:list")
    @GetMapping("/list")
    public TableDataInfo<WmArrivalNoticeLineVo> list(WmArrivalNoticeLineBo bo, PageQuery pageQuery) {
        return wmArrivalNoticeLineService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出到货通知单行列表
     */
    @SaCheckPermission("wm:arrivalNoticeLine:export")
    @Log(title = "到货通知单行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmArrivalNoticeLineBo bo, HttpServletResponse response) {
        List<WmArrivalNoticeLineVo> list = wmArrivalNoticeLineService.selectList(bo);
        ExcelUtil.exportExcel(list, "到货通知单行数据", WmArrivalNoticeLineVo.class, response);
    }

    /**
     * 获取到货通知单行详细信息
     */
    @SaCheckPermission("wm:arrivalNoticeLine:query")
    @GetMapping("/{id}")
    public R<WmArrivalNoticeLineVo> getInfo(@PathVariable Long id) {
        return R.ok(wmArrivalNoticeLineService.selectById(id));
    }

    /**
     * 新增到货通知单行
     */
    @SaCheckPermission("wm:arrivalNoticeLine:add")
    @Log(title = "到货通知单行", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmArrivalNoticeLineBo bo) {
        return toAjax(wmArrivalNoticeLineService.insertByBo(bo));
    }

    /**
     * 修改到货通知单行
     */
    @SaCheckPermission("wm:arrivalNoticeLine:edit")
    @Log(title = "到货通知单行", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmArrivalNoticeLineBo bo) {
        return toAjax(wmArrivalNoticeLineService.updateByBo(bo));
    }

    /**
     * 删除到货通知单行
     */
    @SaCheckPermission("wm:arrivalNoticeLine:remove")
    @Log(title = "到货通知单行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmArrivalNoticeLineService.deleteByIds(ids));
    }
}
