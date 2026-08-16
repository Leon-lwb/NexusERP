package com.nexus.mes.fw.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.fw.domain.bo.FwTestItemBo;
import com.nexus.mes.fw.domain.vo.FwTestItemVo;
import com.nexus.mes.fw.service.IFwTestItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES测试项标准 Controller
 * <p>按物料/固件范围配置测试标准。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/fw/test-item")
public class FwTestItemController extends BaseController {

    private final IFwTestItemService fwTestItemService;

    /**
     * 分页查询测试项标准
     */
    @SaCheckPermission("mes:fw:testitem:list")
    @GetMapping("/list")
    public TableDataInfo<FwTestItemVo> list(FwTestItemBo bo, PageQuery pageQuery) {
        return fwTestItemService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取测试项详情
     */
    @SaCheckPermission("mes:fw:testitem:query")
    @GetMapping("/{testItemId}")
    public R<FwTestItemVo> getInfo(@PathVariable Long testItemId) {
        return R.ok(fwTestItemService.selectById(testItemId));
    }

    /**
     * 新增测试项
     */
    @SaCheckPermission("mes:fw:testitem:add")
    @Log(title = "测试项新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody FwTestItemBo bo) {
        return toAjax(fwTestItemService.insertByBo(bo));
    }

    /**
     * 修改测试项
     */
    @SaCheckPermission("mes:fw:testitem:edit")
    @Log(title = "测试项修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody FwTestItemBo bo) {
        return toAjax(fwTestItemService.updateByBo(bo));
    }

    /**
     * 批量删除测试项(被测试记录引用时禁止)
     */
    @SaCheckPermission("mes:fw:testitem:remove")
    @Log(title = "测试项删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(fwTestItemService.deleteByIds(ids));
    }

    /**
     * 按物料+固件范围查询可用测试项(开单快照/下拉)
     */
    @SaCheckPermission("mes:fw:testitem:list")
    @GetMapping("/by-material")
    public R<List<FwTestItemVo>> byMaterial(@RequestParam Long materialId, @RequestParam(required = false) Long firmwareId) {
        return R.ok(fwTestItemService.selectByMaterial(materialId, firmwareId));
    }
}