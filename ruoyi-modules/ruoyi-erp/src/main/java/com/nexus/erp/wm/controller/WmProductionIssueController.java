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
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产领料Controller
 * <p>
 * 提供生产领料单的 RESTful API 接口，包括 CRUD 操作、数据导出及执行领料等功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/productionissue")
public class WmProductionIssueController extends BaseController {

    private final IWmProductionIssueService wmProductionIssueService;

    /**
     * 分页查询生产领料列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产领料分页列表
     */
    @SaCheckPermission("wm:productionissue:list")
    @GetMapping("/list")
    public TableDataInfo<WmProductionIssueVo> list(WmProductionIssueBo bo, PageQuery pageQuery) {
        return wmProductionIssueService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产领料数据到 Excel
     * <p>权限标识：{@code wm:productionissue:export}</p>
     * <p>日志记录：title="生产领料"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象
     */
    @SaCheckPermission("wm:productionissue:export")
    @Log(title = "生产领料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmProductionIssueBo bo, HttpServletResponse response) {
        List<WmProductionIssueVo> list = wmProductionIssueService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产领料数据", WmProductionIssueVo.class, response);
    }

    /**
     * 获取生产领料详细信息
     * <p>权限标识：{@code wm:productionissue:query}</p>
     *
     * @param issueId 生产领料ID
     * @return 生产领料详细信息
     */
    @SaCheckPermission("wm:productionissue:query")
    @GetMapping("/{issueId}")
    public R<WmProductionIssueVo> getInfo(@PathVariable Long issueId) {
        return R.ok(wmProductionIssueService.selectById(issueId));
    }

    /**
     * 新增生产领料
     * <p>权限标识：{@code wm:productionissue:add}</p>
     * <p>日志记录：title="生产领料"，businessType=INSERT</p>
     *
     * @param bo 生产领料业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:productionissue:add")
    @Log(title = "生产领料", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmProductionIssueBo bo) {
        return toAjax(wmProductionIssueService.insertByBo(bo));
    }

    /**
     * 修改生产领料
     * <p>权限标识：{@code wm:productionissue:edit}</p>
     * <p>日志记录：title="生产领料"，businessType=UPDATE</p>
     *
     * @param bo 生产领料业务对象
     * @return 操作结果
     */
    @SaCheckPermission("wm:productionissue:edit")
    @Log(title = "生产领料", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmProductionIssueBo bo) {
        return toAjax(wmProductionIssueService.updateByBo(bo));
    }

    /**
     * 删除生产领料
     * <p>权限标识：{@code wm:productionissue:remove}</p>
     * <p>日志记录：title="生产领料"，businessType=DELETE</p>
     *
     * @param issueIds 生产领料ID数组
     * @return 操作结果
     */
    @SaCheckPermission("wm:productionissue:remove")
    @Log(title = "生产领料", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public R<Void> remove(@PathVariable Long[] issueIds) {
        return toAjax(wmProductionIssueService.deleteByIds(issueIds));
    }

    /**
     * 执行生产领料（扣减库存）
     * <p>权限标识：{@code wm:productionissue:edit}</p>
     * <p>日志记录：title="生产领料"，businessType=UPDATE</p>
     *
     * @param issueId 生产领料ID
     * @return 操作结果
     */
    @SaCheckPermission("wm:productionissue:edit")
    @Log(title = "生产领料", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{issueId}")
    public R<Void> execute(@PathVariable Long issueId) {
        wmProductionIssueService.execute(issueId);
        return R.ok();
    }

    /**
     * 查询物料可用库存（ATP），按 FEFO 排序
     * <p>权限标识：{@code wm:productionissue:query}</p>
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID（可空）
     * @return 可用库存明细列表
     */
    @SaCheckPermission("wm:productionissue:query")
    @GetMapping("/availableStock")
    public R<List<WmMaterialStockVo>> availableStock(@RequestParam Long materialId,
                                                      @RequestParam(required = false) Long warehouseId) {
        return R.ok(wmProductionIssueService.selectAvailableStock(materialId, warehouseId));
    }

    /**
     * 根据工单 BOM 物料需求构建领料单行（排除倒冲物料）
     * <p>权限标识：{@code wm:productionissue:query}</p>
     * <p>前端"选择工单后自动带出应领明细"调用。</p>
     *
     * @param woId 工单ID
     * @return 领料单行列表（未持久化）
     */
    @SaCheckPermission("wm:productionissue:query")
    @GetMapping("/workOrderLines/{woId}")
    public R<List<WmProductionIssueLine>> workOrderLines(@PathVariable Long woId) {
        return R.ok(wmProductionIssueService.buildLinesFromWorkOrder(woId));
    }
}
