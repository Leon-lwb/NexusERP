package com.nexus.erp.bd.controller;

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
import com.nexus.erp.bd.domain.bo.BdRoutingBo;
import com.nexus.erp.bd.domain.vo.BdRoutingStepVo;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;
import com.nexus.erp.bd.service.IBdRoutingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工艺路线Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/routing")
public class BdRoutingController extends BaseController {

    private final IBdRoutingService bdRoutingService;

    /**
     * 分页查询工艺路线列表
     */
    @SaCheckPermission("bd:routing:list")
    @GetMapping("/list")
    public TableDataInfo<BdRoutingVo> list(BdRoutingBo bo, PageQuery pageQuery) {
        return bdRoutingService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工艺路线数据到 Excel
     */
    @SaCheckPermission("bd:routing:export")
    @Log(title = "工艺路线", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdRoutingBo bo, HttpServletResponse response) {
        List<BdRoutingVo> list = bdRoutingService.selectList(bo);
        ExcelUtil.exportExcel(list, "工艺路线数据", BdRoutingVo.class, response);
    }

    /**
     * 根据主键获取工艺路线详细信息
     */
    @SaCheckPermission("bd:routing:query")
    @GetMapping("/{id}")
    public R<BdRoutingVo> getInfo(@PathVariable Long id) {
        return R.ok(bdRoutingService.selectById(id));
    }

    /**
     * 新增工艺路线
     */
    @SaCheckPermission("bd:routing:add")
    @Log(title = "工艺路线", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdRoutingBo bo) {
        return toAjax(bdRoutingService.insertByBo(bo));
    }

    /**
     * 修改工艺路线
     */
    @SaCheckPermission("bd:routing:edit")
    @Log(title = "工艺路线", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdRoutingBo bo) {
        return toAjax(bdRoutingService.updateByBo(bo));
    }

    /**
     * 批量删除工艺路线
     */
    @SaCheckPermission("bd:routing:remove")
    @Log(title = "工艺路线", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdRoutingService.deleteByIds(ids));
    }

    /**
     * 激活工艺路线版本
     */
    @SaCheckPermission("bd:routing:edit")
    @Log(title = "工艺路线", businessType = BusinessType.UPDATE)
    @PutMapping("/activateVersion/{routingId}")
    public R<Void> activateVersion(@PathVariable Long routingId) {
        bdRoutingService.activateVersion(routingId);
        return R.ok();
    }

    /**
     * 查询物料的当前有效工艺路线
     */
    @SaCheckPermission("bd:routing:query")
    @GetMapping("/current/{materialId}")
    public R<BdRoutingVo> getCurrentRouting(@PathVariable Long materialId) {
        return R.ok(bdRoutingService.getCurrentRouting(materialId));
    }

    /**
     * 获取工艺路线的工序步骤列表
     */
    @SaCheckPermission("bd:routing:query")
    @GetMapping("/steps/{routingId}")
    public R<List<BdRoutingStepVo>> getRoutingSteps(@PathVariable Long routingId) {
        return R.ok(bdRoutingService.getRoutingSteps(routingId));
    }

    /**
     * 校验工艺路线
     */
    @SaCheckPermission("bd:routing:query")
    @GetMapping("/verify/{routingId}")
    public R<String> verify(@PathVariable Long routingId) {
        String result = bdRoutingService.verifyRouting(routingId);
        return R.ok(result);
    }
}
