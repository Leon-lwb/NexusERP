package com.nexus.mes.label.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.domain.vo.MesLabelTemplateVo;
import com.nexus.mes.label.domain.vo.LabelPrintTaskVo;
import com.nexus.mes.label.service.IMesLabelTemplateService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * 标签打印模板管理 Controller(M1 标签打印)
 * <p>模板由 plus-ui「标签模板」页维护; SN 造码后由 SnLabelPrintEventListener 自动渲染直发。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/label/template")
public class MesLabelTemplateController extends BaseController {

    private final IMesLabelTemplateService labelTemplateService;

    /**
     * 分页查询标签模板列表
     */
    @SaCheckPermission("mes:label:template:list")
    @GetMapping("/list")
    public TableDataInfo<MesLabelTemplateVo> list(MesLabelTemplateBo bo, PageQuery pageQuery) {
        return labelTemplateService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取标签模板详情
     */
    @SaCheckPermission("mes:label:template:query")
    @GetMapping("/{templateId}")
    public R<MesLabelTemplateVo> getInfo(@NotNull @PathVariable Long templateId) {
        return R.ok(labelTemplateService.selectById(templateId));
    }

    /**
     * 新增标签模板
     */
    @SaCheckPermission("mes:label:template:add")
    @Log(title = "标签模板", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody MesLabelTemplateBo bo) {
        return toAjax(labelTemplateService.insertByBo(bo));
    }

    /**
     * 编辑标签模板
     */
    @SaCheckPermission("mes:label:template:edit")
    @Log(title = "标签模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody MesLabelTemplateBo bo) {
        return toAjax(labelTemplateService.updateByBo(bo));
    }

    /**
     * 批量删除标签模板
     */
    @SaCheckPermission("mes:label:template:remove")
    @Log(title = "标签模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] templateIds) {
        return toAjax(labelTemplateService.deleteByIds(Arrays.asList(templateIds)));
    }

    /**
     * 渲染预览: 样例数据渲染指令串(不发送打印机)
     */
    @SaCheckPermission("mes:label:template:list")
    @GetMapping("/{templateId}/preview")
    public R<Map<String, Object>> preview(@NotNull @PathVariable Long templateId) {
        return R.ok(labelTemplateService.preview(templateId));
    }

    /**
     * 测试打印先保存任务，再按已提交租约发送，返回准确状态供人工核验。
     */
    @SaCheckPermission("mes:label:template:print")
    @Log(title = "标签模板", businessType = BusinessType.OTHER)
    @PostMapping("/{templateId}/test-print")
    public R<LabelPrintTaskVo> testPrint(
            @NotNull @PathVariable Long templateId,
            @RequestParam(required = false) String requestId) {
        return receipt(labelTemplateService.testPrintTask(templateId, requestId));
    }

    /**
     * 保留旧Java调用签名，HTTP参数requestId为可选新增字段。
     */
    public R<LabelPrintTaskVo> testPrint(Long templateId) {
        return testPrint(templateId, null);
    }

    /**
     * 补打保留原URL，原任务及原因现在必须明确提供，绝不自动重发 UNKNOWN。
     */
    @SaCheckPermission("mes:label:template:print")
    @Log(title = "标签补打", businessType = BusinessType.OTHER)
    @PostMapping("/reprint")
    public R<LabelPrintTaskVo> reprint(
            @NotNull @RequestParam Long snId,
            @NotNull @RequestParam Long templateId,
            @RequestParam(required = false) String printSource,
            @RequestParam(required = false) Long originalPrintLogId,
            @RequestParam(required = false) String reprintReason,
            @RequestParam(required = false) String requestId) {
        if (printSource != null && !"REPRINT".equals(printSource)) {
            throw new ServiceException("补打来源只允许 REPRINT");
        }
        return receipt(
                labelTemplateService.reprintTask(
                        snId, templateId, originalPrintLogId, reprintReason, requestId));
    }

    /**
     * 保留旧Java签名，缺少原任务及原因由实际服务明确拒绝。
     */
    public R<LabelPrintTaskVo> reprint(Long snId, Long templateId) {
        return reprint(snId, templateId, null, null, null, null);
    }

    /**
     * 保留上一版五参数Java签名。
     */
    public R<LabelPrintTaskVo> reprint(
            Long snId,
            Long templateId,
            String printSource,
            Long originalPrintLogId,
            String reprintReason) {
        return reprint(snId, templateId, printSource, originalPrintLogId, reprintReason, null);
    }

    /**
     * 人工查询待发或 UNKNOWN 的原任务，保留原权限及租户隔离。
     */
    @SaCheckPermission("mes:label:template:print")
    @GetMapping("/print-task/{taskId}")
    public R<LabelPrintTaskVo> printTask(@NotNull @PathVariable Long taskId) {
        return receipt(labelTemplateService.printTask(taskId));
    }

    /**
     * 成功接收任务不等于物理打印成功；失败和未知也返回持久化ID，禁止前端自动重发。
     */
    private R<LabelPrintTaskVo> receipt(LabelPrintTaskVo task) {
        String message =
                switch (task.getStatus()) {
                    case "SENT" -> "打印指令已发送，未确认打印机实际出纸";
                    case "UNKNOWN" -> "打印状态未知，请核验原任务，禁止直接重发";
                    case "FAIL" -> "打印任务失败，核实后需按原任务及原因显式补打";
                    case "LEGACY_SENT" -> "已有历史发送留痕，本任务未发送；实际出纸未知";
                    default -> "打印任务已持久化，等待发送或正在发送";
                };
        return R.ok(message, task);
    }
}
