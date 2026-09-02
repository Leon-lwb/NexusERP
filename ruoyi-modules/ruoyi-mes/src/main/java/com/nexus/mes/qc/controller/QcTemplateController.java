package com.nexus.mes.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.qc.domain.bo.QcInspectTemplateBo;
import com.nexus.mes.qc.domain.vo.QcInspectTemplateVo;
import com.nexus.mes.qc.service.IQcTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * QC检验模板 Controller
 * <p>
 * 检验标准(模板+项目)维护: 检验单创建时按模板快照生成明细,
 * 录入实测值后由判定引擎自动给出 PASS/FAIL。
 * </p>
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/qc/template")
public class QcTemplateController extends BaseController {

    private final IQcTemplateService qcTemplateService;

    /**
     * 分页查询检验模板列表
     */
    @SaCheckPermission("mes:qc:template:list")
    @GetMapping("/list")
    public TableDataInfo<QcInspectTemplateVo> list(QcInspectTemplateBo bo, PageQuery pageQuery) {
        return qcTemplateService.selectPageList(bo, pageQuery);
    }

    /**
     * 获取检验模板详情(含项目明细)
     */
    @SaCheckPermission("mes:qc:template:query")
    @GetMapping("/{templateId}")
    public R<QcInspectTemplateVo> getInfo(@PathVariable Long templateId) {
        return R.ok(qcTemplateService.selectById(templateId));
    }

    /**
     * 新增检验模板(项目明细随模板整体提交)
     */
    @SaCheckPermission("mes:qc:template:add")
    @Log(title = "检验模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody QcInspectTemplateBo bo) {
        return toAjax(qcTemplateService.insertByBo(bo));
    }

    /**
     * 修改检验模板(项目明细先删后插)
     */
    @SaCheckPermission("mes:qc:template:edit")
    @Log(title = "检验模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody QcInspectTemplateBo bo) {
        return toAjax(qcTemplateService.updateByBo(bo));
    }

    /**
     * 删除检验模板(校验无检验明细引用)
     */
    @SaCheckPermission("mes:qc:template:remove")
    @Log(title = "检验模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(qcTemplateService.deleteByIds(ids));
    }

    /**
     * 启用模板
     */
    @SaCheckPermission("mes:qc:template:edit")
    @Log(title = "检验模板", businessType = BusinessType.UPDATE)
    @PutMapping("/activate/{templateId}")
    public R<Void> activate(@PathVariable Long templateId) {
        qcTemplateService.changeStatus(templateId, true);
        return R.ok();
    }

    /**
     * 停用模板(检验单创建时不再匹配)
     */
    @SaCheckPermission("mes:qc:template:edit")
    @Log(title = "检验模板", businessType = BusinessType.UPDATE)
    @PutMapping("/deactivate/{templateId}")
    public R<Void> deactivate(@PathVariable Long templateId) {
        qcTemplateService.changeStatus(templateId, false);
        return R.ok();
    }

    /**
     * 版本复制: 复制模板及项目为新版本草稿
     */
    @SaCheckPermission("mes:qc:template:copy")
    @Log(title = "检验模板", businessType = BusinessType.INSERT)
    @PostMapping("/copy/{templateId}")
    public R<Long> copyVersion(@PathVariable Long templateId) {
        return R.ok(qcTemplateService.copyVersion(templateId));
    }
}
