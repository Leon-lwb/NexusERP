package com.nexus.mes.sop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.sop.domain.bo.SopProcessBindingBo;
import com.nexus.mes.sop.domain.vo.SopProcessBindingVo;
import com.nexus.mes.sop.service.ISopProcessBindingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工序SOP绑定 Controller
 * <p>工序-SOP绑定 CRUD + 按工序查询绑定列表。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/sop/binding")
public class SopProcessBindingController extends BaseController {

    private final ISopProcessBindingService sopProcessBindingService;

    /**
     * 分页查询绑定列表
     */
    @SaCheckPermission("mes:sop:binding:list")
    @GetMapping("/list")
    public TableDataInfo<SopProcessBindingVo> list(SopProcessBindingBo bo, PageQuery pageQuery) {
        return sopProcessBindingService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取绑定详情
     */
    @SaCheckPermission("mes:sop:binding:query")
    @GetMapping("/{bindingId}")
    public R<SopProcessBindingVo> getInfo(@PathVariable Long bindingId) {
        return R.ok(sopProcessBindingService.selectById(bindingId));
    }

    /**
     * 新增绑定(is_default=1 时同工序排他置0)
     */
    @SaCheckPermission("mes:sop:binding:add")
    @Log(title = "工序SOP绑定新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody SopProcessBindingBo bo) {
        return toAjax(sopProcessBindingService.insertByBo(bo));
    }

    /**
     * 批量删除绑定
     */
    @SaCheckPermission("mes:sop:binding:remove")
    @Log(title = "工序SOP绑定删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(sopProcessBindingService.deleteByIds(ids));
    }

    /**
     * 查询工序绑定的SOP列表(is_default 优先 + sort_order)
     */
    @SaCheckPermission("mes:sop:binding:list")
    @GetMapping("/by-process/{processId}")
    public R<List<SopProcessBindingVo>> byProcess(@PathVariable Long processId) {
        return R.ok(sopProcessBindingService.selectByProcess(processId));
    }
}
