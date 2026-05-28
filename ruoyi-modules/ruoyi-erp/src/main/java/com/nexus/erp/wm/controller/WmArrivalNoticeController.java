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
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;
import com.nexus.erp.wm.service.IWmArrivalNoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 到货通知 Controller
 * <p>
 * 提供到货通知单的 RESTful API 接口，用于管理供应商到货通知信息。
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
     * 分页查询到货通知列表
     * <p>
     * 权限标识：{@code wm:arrivalNotice:list}</p>
     *
     * @param bo        查询参数，包含通知单号、供应商、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 到货通知分页列表
     */
    @SaCheckPermission("wm:arrivalNotice:list")
    @GetMapping("/list")
    public TableDataInfo<WmArrivalNoticeVo> list(WmArrivalNoticeBo bo, PageQuery pageQuery) {
        return wmArrivalNoticeService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出到货通知数据到 Excel
     * <p>权限标识：{@code wm:arrivalNotice:export}</p>
     * <p>日志记录：title="到货通知"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:arrivalNotice:export")
    @Log(title = "到货通知", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmArrivalNoticeBo bo, HttpServletResponse response) {
        List<WmArrivalNoticeVo> list = wmArrivalNoticeService.selectList(bo);
        ExcelUtil.exportExcel(list, "到货通知数据", WmArrivalNoticeVo.class, response);
    }

    /**
     * 根据主键获取到货通知详细信息
     * <p>
     * 权限标识：{@code wm:arrivalNotice:query}</p>
     *
     * @param id 到货通知 ID
     * @return 到货通知详细信息
     */
    @SaCheckPermission("wm:arrivalNotice:query")
    @GetMapping("/{id}")
    public R<WmArrivalNoticeVo> getInfo(@PathVariable Long id) {
        return R.ok(wmArrivalNoticeService.selectById(id));
    }

    /**
     * 新增到货通知
     * <p>
     * 权限标识：{@code wm:arrivalNotice:add}</p>
     * <p>日志记录：title="到货通知"，businessType=INSERT</p>
     *
     * @param bo 到货通知业务对象，包含供应商信息、到货物料明细等
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNotice:add")
    @Log(title = "到货通知", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmArrivalNoticeBo bo) {
        return toAjax(wmArrivalNoticeService.insertByBo(bo));
    }

    /**
     * 修改到货通知
     * <p>
     * 权限标识：{@code wm:arrivalNotice:edit}</p>
     * <p>日志记录：title="到货通知"，businessType=UPDATE</p>
     *
     * @param bo 到货通知业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNotice:edit")
    @Log(title = "到货通知", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmArrivalNoticeBo bo) {
        return toAjax(wmArrivalNoticeService.updateByBo(bo));
    }

    /**
     * 批量删除到货通知
     * <p>
     * 权限标识：{@code wm:arrivalNotice:remove}</p>
     * <p>日志记录：title="到货通知"，businessType=DELETE</p>
     *
     * @param ids 要删除的到货通知 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNotice:remove")
    @Log(title = "到货通知", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmArrivalNoticeService.deleteByIds(ids));
    }
}
