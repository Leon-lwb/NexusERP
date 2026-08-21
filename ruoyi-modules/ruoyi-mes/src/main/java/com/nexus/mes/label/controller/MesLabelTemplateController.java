package com.nexus.mes.label.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.domain.vo.MesLabelTemplateVo;
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
     * 测试打印: 样例数据渲染并直发打印机(需打印机在线)
     */
    @SaCheckPermission("mes:label:template:print")
    @Log(title = "标签模板", businessType = BusinessType.OTHER)
    @PostMapping("/{templateId}/test-print")
    public R<Void> testPrint(@NotNull @PathVariable Long templateId) {
        boolean success = labelTemplateService.testPrint(templateId);
        if (success) {
            return R.ok("测试打印已发送", null);
        }
        return R.fail("打印发送失败(打印机不在线或地址未配置), 详情见服务端日志");
    }
}
