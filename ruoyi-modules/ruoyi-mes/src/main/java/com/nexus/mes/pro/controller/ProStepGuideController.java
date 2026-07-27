package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProStepGuideBo;
import com.nexus.mes.pro.domain.vo.ProStepGuideVo;
import com.nexus.mes.pro.service.IProStepGuideService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * MES工序流程指引配置 Controller(P0-3)
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/step-guide")
public class ProStepGuideController extends BaseController {

    private final IProStepGuideService proStepGuideService;

    /**
     * 分页查询工序流程指引列表
     */
    @SaCheckPermission("mes:pro:step-guide:list")
    @GetMapping("/list")
    public TableDataInfo<ProStepGuideVo> list(ProStepGuideBo bo, PageQuery pageQuery) {
        return proStepGuideService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出工序流程指引数据到Excel
     */
    @SaCheckPermission("mes:pro:step-guide:export")
    @Log(title = "工序流程指引", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProStepGuideBo bo, HttpServletResponse response) {
        List<ProStepGuideVo> list = proStepGuideService.selectList(bo);
        ExcelUtil.exportExcel(list, "工序流程指引", ProStepGuideVo.class, response);
    }

    /**
     * 根据ID获取工序流程指引详情
     */
    @SaCheckPermission("mes:pro:step-guide:query")
    @GetMapping("/{guideId}")
    public R<ProStepGuideVo> getInfo(@NotNull @PathVariable Long guideId) {
        return R.ok(proStepGuideService.selectById(guideId));
    }

    /**
     * 新增工序流程指引
     */
    @SaCheckPermission("mes:pro:step-guide:add")
    @Log(title = "工序流程指引", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody ProStepGuideBo bo) {
        return toAjax(proStepGuideService.insertByBo(bo));
    }

    /**
     * 编辑工序流程指引
     */
    @SaCheckPermission("mes:pro:step-guide:edit")
    @Log(title = "工序流程指引", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody ProStepGuideBo bo) {
        return toAjax(proStepGuideService.updateByBo(bo));
    }

    /**
     * 批量删除工序流程指引
     */
    @SaCheckPermission("mes:pro:step-guide:remove")
    @Log(title = "工序流程指引", businessType = BusinessType.DELETE)
    @DeleteMapping("/{guideIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] guideIds) {
        return toAjax(proStepGuideService.deleteByIds(Arrays.asList(guideIds)));
    }
}
