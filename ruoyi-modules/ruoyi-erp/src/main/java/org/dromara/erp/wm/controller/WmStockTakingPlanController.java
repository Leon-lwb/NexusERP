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
import org.dromara.erp.wm.domain.bo.WmStockTakingPlanBo;
import org.dromara.erp.wm.domain.vo.WmStockTakingPlanVo;
import org.dromara.erp.wm.service.IWmStockTakingPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 盘点管理Controller
 * <p>
 * 提供库存盘点计划的 RESTful API 接口，包括 CRUD 操作、数据导出、生成盘点明细及过账调整等功能。
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stocktaking")
public class WmStockTakingPlanController extends BaseController {

    private final IWmStockTakingPlanService wmStockTakingPlanService;

    /**
     * 分页查询盘点计划列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 盘点计划分页列表
     */
    @SaCheckPermission("wm:stocktaking:list")
    @GetMapping("/list")
    public TableDataInfo<WmStockTakingPlanVo> list(WmStockTakingPlanBo bo, PageQuery pageQuery) {
        return wmStockTakingPlanService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出盘点数据到 Excel
     * <p>权限标识：{@code wm:stocktaking:export}</p>
     * <p>日志记录：title="盘点管理"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:stocktaking:export")
    @Log(title = "盘点管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStockTakingPlanBo bo, HttpServletResponse response) {
        List<WmStockTakingPlanVo> list = wmStockTakingPlanService.selectList(bo);
        ExcelUtil.exportExcel(list, "盘点数据", WmStockTakingPlanVo.class, response);
    }

    /**
     * 获取盘点计划详细信息
     * <p>权限标识：{@code wm:stocktaking:query}</p>
     *
     * @param planId 盘点计划ID
     * @return 盘点计划详细信息
     */
    @SaCheckPermission("wm:stocktaking:query")
    @GetMapping("/{planId}")
    public R<WmStockTakingPlanVo> getInfo(@PathVariable Long planId) {
        return R.ok(wmStockTakingPlanService.selectById(planId));
    }

    /**
     * 新增盘点计划
     * <p>权限标识：{@code wm:stocktaking:add}</p>
     * <p>日志记录：title="盘点管理"，businessType=INSERT</p>
     *
     * @param bo 盘点计划业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:stocktaking:add")
    @Log(title = "盘点管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmStockTakingPlanBo bo) {
        return toAjax(wmStockTakingPlanService.insertByBo(bo));
    }

    /**
     * 修改盘点计划
     * <p>权限标识：{@code wm:stocktaking:edit}</p>
     * <p>日志记录：title="盘点管理"，businessType=UPDATE</p>
     *
     * @param bo 盘点计划业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStockTakingPlanBo bo) {
        return toAjax(wmStockTakingPlanService.updateByBo(bo));
    }

    /**
     * 删除盘点计划
     * <p>权限标识：{@code wm:stocktaking:remove}</p>
     * <p>日志记录：title="盘点管理"，businessType=DELETE</p>
     *
     * @param planIds 盘点计划ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:stocktaking:remove")
    @Log(title = "盘点管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@PathVariable Long[] planIds) {
        return toAjax(wmStockTakingPlanService.deleteByIds(planIds));
    }

    /**
     * 根据盘点计划生成盘点明细行
     * <p>权限标识：{@code wm:stocktaking:edit}</p>
     * <p>日志记录：title="盘点管理"，businessType=UPDATE</p>
     *
     * @param planId 盘点计划ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping("/generateLines/{planId}")
    public R<Void> generateLines(@PathVariable Long planId) {
        wmStockTakingPlanService.generateLines(planId);
        return R.ok();
    }

    /**
     * 过账盘点调整（根据盘点结果调整库存）
     * <p>权限标识：{@code wm:stocktaking:edit}</p>
     * <p>日志记录：title="盘点管理"，businessType=UPDATE</p>
     *
     * @param planId 盘点计划ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:stocktaking:edit")
    @Log(title = "盘点管理", businessType = BusinessType.UPDATE)
    @PutMapping("/postAdjustment/{planId}")
    public R<Void> postAdjustment(@PathVariable Long planId) {
        wmStockTakingPlanService.postAdjustment(planId);
        return R.ok();
    }
}
