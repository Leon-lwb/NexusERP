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
import org.dromara.erp.wm.domain.bo.WmArrivalNoticeBo;
import org.dromara.erp.wm.domain.vo.WmArrivalNoticeVo;
import org.dromara.erp.wm.service.IWmArrivalNoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 到货通知Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/arrivalnotice")
public class WmArrivalNoticeController extends BaseController {

    private final IWmArrivalNoticeService wmArrivalNoticeService;

    /**
     * 查询到货通知列表
     */
    @SaCheckPermission("wm:arrivalNotice:list")
    @GetMapping("/list")
    public TableDataInfo<WmArrivalNoticeVo> list(WmArrivalNoticeBo bo, PageQuery pageQuery) {
        return wmArrivalNoticeService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出到货通知列表
     */
    @SaCheckPermission("wm:arrivalNotice:export")
    @Log(title = "到货通知", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmArrivalNoticeBo bo, HttpServletResponse response) {
        List<WmArrivalNoticeVo> list = wmArrivalNoticeService.selectList(bo);
        ExcelUtil.exportExcel(list, "到货通知数据", WmArrivalNoticeVo.class, response);
    }

    /**
     * 获取到货通知详细信息
     */
    @SaCheckPermission("wm:arrivalNotice:query")
    @GetMapping("/{id}")
    public R<WmArrivalNoticeVo> getInfo(@PathVariable Long id) {
        return R.ok(wmArrivalNoticeService.selectById(id));
    }

    /**
     * 新增到货通知
     */
    @SaCheckPermission("wm:arrivalNotice:add")
    @Log(title = "到货通知", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmArrivalNoticeBo bo) {
        return toAjax(wmArrivalNoticeService.insertByBo(bo));
    }

    /**
     * 修改到货通知
     */
    @SaCheckPermission("wm:arrivalNotice:edit")
    @Log(title = "到货通知", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmArrivalNoticeBo bo) {
        return toAjax(wmArrivalNoticeService.updateByBo(bo));
    }

    /**
     * 删除到货通知
     */
    @SaCheckPermission("wm:arrivalNotice:remove")
    @Log(title = "到货通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmArrivalNoticeService.deleteByIds(ids));
    }
}
