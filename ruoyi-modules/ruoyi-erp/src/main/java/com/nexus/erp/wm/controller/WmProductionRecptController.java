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
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 生产收货管理 Controller
 * <p>
 * 提供生产收货相关的 REST 接口，包括分页查询、新增、修改、删除、导入导出等操作。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/productionrecpt")
public class WmProductionRecptController extends BaseController {

    /** 生产收货业务服务，提供生产收货相关能力 */
    private final IWmProductionRecptService wmProductionRecptService;

    /**
     * 分页查询生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:list")
    @GetMapping("/list")
    public TableDataInfo<WmProductionRecptVo> list(WmProductionRecptBo bo, PageQuery pageQuery) {
        return wmProductionRecptService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产收货列表
     *
     * @param bo 生产收货业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:productionrecpt:export")
    @Log(title = "生产入库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmProductionRecptBo bo, HttpServletResponse response) {
        List<WmProductionRecptVo> list = wmProductionRecptService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产入库数据", WmProductionRecptVo.class, response);
    }

    /**
     * 获取生产收货详情
     *
     * @param recptId 收货 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:query")
    @GetMapping("/{recptId}")
    public R<WmProductionRecptVo> getInfo(@PathVariable Long recptId) {
        return R.ok(wmProductionRecptService.selectById(recptId));
    }

    /**
     * 新增生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:add")
    @Log(title = "生产入库", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmProductionRecptBo bo) {
        return toAjax(wmProductionRecptService.insertByBo(bo));
    }

    /**
     * 修改生产收货
     *
     * @param bo 生产收货业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:edit")
    @Log(title = "生产入库", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmProductionRecptBo bo) {
        return toAjax(wmProductionRecptService.updateByBo(bo));
    }

    /**
     * 删除生产收货
     *
     * @param recptIds 参数 recptIds
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:remove")
    @Log(title = "生产入库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recptIds}")
    public R<Void> remove(@PathVariable Long[] recptIds) {
        return toAjax(wmProductionRecptService.deleteByIds(recptIds));
    }

    /**
     * 执行生产收货相关操作
     *
     * @param recptId 收货 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:productionrecpt:edit")
    @Log(title = "生产入库", businessType = BusinessType.UPDATE)
    @PutMapping("/execute/{recptId}")
    public R<Void> execute(@PathVariable Long recptId) {
        wmProductionRecptService.execute(recptId);
        return R.ok();
    }
}
