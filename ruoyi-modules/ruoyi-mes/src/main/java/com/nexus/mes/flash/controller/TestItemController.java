package com.nexus.mes.flash.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.flash.domain.bo.TestItemBo;
import com.nexus.mes.flash.domain.vo.TestItemVo;
import com.nexus.mes.flash.service.ITestItemService;
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
@RequestMapping("/mes/flash/test-item")
public class TestItemController extends BaseController {

    private final ITestItemService mesTestItemService;

    /**
     * 分页查询测试项标准
     */
    @SaCheckPermission("mes:flash:testitem:list")
    @GetMapping("/list")
    public TableDataInfo<TestItemVo> list(TestItemBo bo, PageQuery pageQuery) {
        return mesTestItemService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取测试项详情
     */
    @SaCheckPermission("mes:flash:testitem:query")
    @GetMapping("/{testItemId}")
    public R<TestItemVo> getInfo(@PathVariable Long testItemId) {
        return R.ok(mesTestItemService.selectById(testItemId));
    }

    /**
     * 新增测试项
     */
    @SaCheckPermission("mes:flash:testitem:add")
    @Log(title = "测试项新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody TestItemBo bo) {
        return toAjax(mesTestItemService.insertByBo(bo));
    }

    /**
     * 修改测试项
     */
    @SaCheckPermission("mes:flash:testitem:edit")
    @Log(title = "测试项修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody TestItemBo bo) {
        return toAjax(mesTestItemService.updateByBo(bo));
    }

    /**
     * 批量删除测试项(被测试记录引用时禁止)
     */
    @SaCheckPermission("mes:flash:testitem:remove")
    @Log(title = "测试项删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(mesTestItemService.deleteByIds(ids));
    }

    /**
     * 按物料+固件范围查询可用测试项(开单快照/下拉)
     */
    @SaCheckPermission("mes:flash:testitem:list")
    @GetMapping("/by-material")
    public R<List<TestItemVo>> byMaterial(@RequestParam Long materialId, @RequestParam(required = false) Long contentRefId) {
        return R.ok(mesTestItemService.selectByMaterial(materialId, contentRefId));
    }
}