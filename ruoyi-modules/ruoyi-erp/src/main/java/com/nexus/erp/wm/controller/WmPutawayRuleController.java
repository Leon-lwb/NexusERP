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
import com.nexus.erp.wm.domain.bo.WmPutawayRuleBo;
import com.nexus.erp.wm.domain.vo.WmPutawayRuleVo;
import com.nexus.erp.wm.service.IPutawayRuleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * 上架规则管理 Controller
 * <p>
 * 提供上架规则相关的 REST 接口，包括分页查询、新增、修改、删除、导入导出等操作。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/putawayrule")
public class WmPutawayRuleController extends BaseController {

    /** 上架规则业务服务，提供上架规则相关能力 */
    private final IPutawayRuleService putawayRuleService;

    /**
     * 分页查询上架规则列表
     *
     * @param bo 上架规则业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:putawayrule:list")
    @GetMapping("/list")
    public TableDataInfo<WmPutawayRuleVo> list(WmPutawayRuleBo bo, PageQuery pageQuery) {
        return putawayRuleService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出上架规则列表
     *
     * @param bo 上架规则业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:putawayrule:export")
    @Log(title = "上架策略", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmPutawayRuleBo bo, HttpServletResponse response) {
        List<WmPutawayRuleVo> list = putawayRuleService.selectList(bo);
        ExcelUtil.exportExcel(list, "上架策略数据", WmPutawayRuleVo.class, response);
    }

    /**
     * 获取上架规则详情
     *
     * @param id 主键 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:putawayrule:query")
    @GetMapping("/{id}")
    public R<WmPutawayRuleVo> getInfo(@PathVariable Long id) {
        return R.ok(putawayRuleService.selectById(id));
    }

    /**
     * 新增上架规则
     *
     * @param bo 上架规则业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:putawayrule:add")
    @Log(title = "上架策略", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmPutawayRuleBo bo) {
        return toAjax(putawayRuleService.insertByBo(bo));
    }

    /**
     * 修改上架规则
     *
     * @param bo 上架规则业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:putawayrule:edit")
    @Log(title = "上架策略", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmPutawayRuleBo bo) {
        return toAjax(putawayRuleService.updateByBo(bo));
    }

    /**
     * 删除上架规则
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果对象
     */
    @SaCheckPermission("wm:putawayrule:remove")
    @Log(title = "上架策略", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(putawayRuleService.deleteByIds(ids));
    }
}
