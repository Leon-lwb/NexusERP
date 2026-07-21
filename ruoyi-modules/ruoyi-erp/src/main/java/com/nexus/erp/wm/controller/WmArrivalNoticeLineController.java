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
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeLineBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeLineVo;
import com.nexus.erp.wm.service.IWmArrivalNoticeLineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 到货通知单行 Controller
 * <p>
 * 管理到货通知单的明细行数据，包括物料、数量、批次等信息。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/arrivalnoticeline")
public class WmArrivalNoticeLineController extends BaseController {

    /** 到货通知行业务服务，提供到货通知行相关能力 */
    private final IWmArrivalNoticeLineService wmArrivalNoticeLineService;

    /**
     * 分页查询到货通知单行列表
     * <p>
     * 权限标识：{@code wm:arrivalNoticeLine:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 到货通知单行分页列表
     */
    @SaCheckPermission("wm:arrivalNoticeLine:list")
    @GetMapping("/list")
    public TableDataInfo<WmArrivalNoticeLineVo> list(WmArrivalNoticeLineBo bo, PageQuery pageQuery) {
        return wmArrivalNoticeLineService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出到货通知单行数据到 Excel
     * <p>权限标识：{@code wm:arrivalNoticeLine:export}</p>
     * <p>日志记录：title="到货通知单行"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:arrivalNoticeLine:export")
    @Log(title = "到货通知单行", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmArrivalNoticeLineBo bo, HttpServletResponse response) {
        List<WmArrivalNoticeLineVo> list = wmArrivalNoticeLineService.selectList(bo);
        ExcelUtil.exportExcel(list, "到货通知单行数据", WmArrivalNoticeLineVo.class, response);
    }

    /**
     * 根据主键获取到货通知单行详细信息
     * <p>
     * 权限标识：{@code wm:arrivalNoticeLine:query}</p>
     *
     * @param id 到货通知单行 ID
     * @return 到货通知单行详细信息
     */
    @SaCheckPermission("wm:arrivalNoticeLine:query")
    @GetMapping("/{id}")
    public R<WmArrivalNoticeLineVo> getInfo(@PathVariable Long id) {
        return R.ok(wmArrivalNoticeLineService.selectById(id));
    }

    /**
     * 新增到货通知单行
     * <p>
     * 权限标识：{@code wm:arrivalNoticeLine:add}</p>
     * <p>日志记录：title="到货通知单行"，businessType=INSERT</p>
     *
     * @param bo 到货通知单行业务对象
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNoticeLine:add")
    @Log(title = "到货通知单行", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmArrivalNoticeLineBo bo) {
        return toAjax(wmArrivalNoticeLineService.insertByBo(bo));
    }

    /**
     * 修改到货通知单行
     * <p>
     * 权限标识：{@code wm:arrivalNoticeLine:edit}</p>
     * <p>日志记录：title="到货通知单行"，businessType=UPDATE</p>
     *
     * @param bo 到货通知单行业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNoticeLine:edit")
    @Log(title = "到货通知单行", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmArrivalNoticeLineBo bo) {
        return toAjax(wmArrivalNoticeLineService.updateByBo(bo));
    }

    /**
     * 批量删除到货通知单行
     * <p>
     * 权限标识：{@code wm:arrivalNoticeLine:remove}</p>
     * <p>日志记录：title="到货通知单行"，businessType=DELETE</p>
     *
     * @param ids 要删除的到货通知单行 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:arrivalNoticeLine:remove")
    @Log(title = "到货通知单行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmArrivalNoticeLineService.deleteByIds(ids));
    }
}
