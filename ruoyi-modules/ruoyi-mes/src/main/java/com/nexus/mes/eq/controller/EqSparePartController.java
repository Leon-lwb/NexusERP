package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqSparePartBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;
import com.nexus.mes.eq.service.IEqSparePartService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES备件主数据 Controller
 *
 * <p>属于MES 设备管理模块，提供备件零件相关的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/spare-part")
public class EqSparePartController extends BaseController {

    private final IEqSparePartService eqSparePartService;

    /**
     * 分页查询备件列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:spare:list")
    @GetMapping("/list")
    public TableDataInfo<EqSparePartVo> list(EqSparePartBo bo, PageQuery pageQuery) {
        return eqSparePartService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取备件详情
     *
     * @param spareId 备件ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:query")
    @GetMapping("/{spareId}")
    public R<EqSparePartVo> getInfo(@PathVariable Long spareId) {
        return R.ok(eqSparePartService.selectById(spareId));
    }

    /**
     * 新增备件主数据
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:add")
    @Log(title = "备件主数据", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqSparePartVo> add(@Validated @RequestBody EqSparePartBo bo) {
        return R.ok(eqSparePartService.insertByBo(bo));
    }

    /**
     * 修改备件主数据
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:edit")
    @Log(title = "备件主数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody EqSparePartBo bo) {
        return toAjax(eqSparePartService.updateByBo(bo));
    }

    /**
     * 批量删除备件(库存为0才允许)
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:remove")
    @Log(title = "备件主数据", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(eqSparePartService.deleteByIds(ids));
    }

    /**
     * 导出备件列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("mes:eq:spare:export")
    @Log(title = "备件主数据", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqSparePartBo bo, HttpServletResponse response) {
        List<EqSparePartVo> list = eqSparePartService.selectList(bo);
        ExcelUtil.exportExcel(list, "备件主数据", EqSparePartVo.class, response);
    }

    /**
     * 出入库操作(IN入库 / OUT出库)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:stock")
    @Log(title = "备件出入库", businessType = BusinessType.UPDATE)
    @PostMapping("/stock")
    public R<BigDecimal> stock(@Validated @RequestBody EqSpareStockBo bo) {
        return R.ok("操作成功", eqSparePartService.stockTransaction(bo));
    }

    /**
     * 库存流水分页查询
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:spare:log:list")
    @GetMapping("/stock-log")
    public TableDataInfo<EqSpareStockLogVo> stockLog(EqSpareStockBo bo, PageQuery pageQuery) {
        return eqSparePartService.selectStockLogPage(bo, pageQuery);
    }

    /**
     * 导出库存流水
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("mes:eq:spare:log:export")
    @Log(title = "备件库存流水", businessType = BusinessType.EXPORT)
    @PostMapping("/stock-log/export")
    public void stockLogExport(EqSpareStockBo bo, HttpServletResponse response) {
        List<EqSpareStockLogVo> list = eqSparePartService.selectStockLogList(bo);
        ExcelUtil.exportExcel(list, "备件库存流水", EqSpareStockLogVo.class, response);
    }

    /**
     * 低于安全库存的备件(监控/告警用)
     *
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:spare:query")
    @GetMapping("/lowStock")
    public R<List<EqSparePartVo>> lowStock() {
        return R.ok(eqSparePartService.selectLowStock());
    }
}
