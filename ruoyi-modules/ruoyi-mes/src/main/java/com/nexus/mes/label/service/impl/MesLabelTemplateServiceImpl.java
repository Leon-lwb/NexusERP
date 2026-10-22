package com.nexus.mes.label.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.label.domain.MesLabelPrintLog;
import com.nexus.mes.label.domain.MesLabelTemplate;
import com.nexus.mes.label.domain.bo.MesLabelTemplateBo;
import com.nexus.mes.label.domain.vo.LabelPrintTaskVo;
import com.nexus.mes.label.domain.vo.MesLabelTemplateVo;
import com.nexus.mes.label.mapper.MesLabelTemplateMapper;
import com.nexus.mes.label.service.IMesLabelTemplateService;
import com.nexus.mes.label.service.LabelPrintService;
import com.nexus.mes.label.service.LabelRenderService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 标签打印模板 Service 实现
 * <p>content JSON 在增改时即校验可渲染(防脏模板上线); 预览/测试打印共用样例字段域。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("mesLabelTemplateService")
public class MesLabelTemplateServiceImpl implements IMesLabelTemplateService {

    private static final int DEFAULT_PORT = 9100;

    private final MesLabelTemplateMapper baseMapper;
    private final LabelRenderService renderService;
    private final LabelPrintService printService;
    private final ProSnMapper proSnMapper;

    @Override
    public TableDataInfo<MesLabelTemplateVo> selectPageList(
            MesLabelTemplateBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<MesLabelTemplate> lqw = buildQueryWrapper(bo);
        Page<MesLabelTemplateVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public MesLabelTemplateVo selectById(Long templateId) {
        return baseMapper.selectVoById(templateId);
    }

    @Override
    public List<MesLabelTemplateVo> selectList(MesLabelTemplateBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(MesLabelTemplateBo bo) {
        MesLabelTemplate template = MapstructUtils.convert(bo, MesLabelTemplate.class);
        applyDefaults(template);
        validateRenderable(template);
        checkCodeUnique(template);
        template.setTemplateRevision(1L);
        return baseMapper.insert(template) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(MesLabelTemplateBo bo) {
        if (bo.getTemplateId() == null) {
            throw new ServiceException("模板ID不能为空");
        }
        MesLabelTemplate template = MapstructUtils.convert(bo, MesLabelTemplate.class);
        applyDefaults(template);
        validateRenderable(template);
        checkCodeUnique(template);
        MesLabelTemplate current = requireTemplate(template.getTemplateId());
        Long revision = current.getTemplateRevision();
        if (bo.getTemplateRevision() != null
                && !Objects.equals(bo.getTemplateRevision(), revision)) {
            throw new ServiceException("模板已被修改，请重新加载后保存");
        }
        template.setTemplateRevision(revision == null ? 1L : Math.addExact(revision, 1L));
        int updated =
                baseMapper.update(
                        template,
                        Wrappers.<MesLabelTemplate>lambdaUpdate()
                                .eq(MesLabelTemplate::getTemplateId, template.getTemplateId())
                                .eq(
                                        revision != null,
                                        MesLabelTemplate::getTemplateRevision,
                                        revision)
                                .isNull(revision == null, MesLabelTemplate::getTemplateRevision));
        if (updated == 0) {
            throw new ServiceException("模板已被修改，请重新加载后保存");
        }
        return true;
    }

    @Override
    public Boolean deleteByIds(Collection<Long> ids) {
        return baseMapper.deleteByIds(ids) > 0;
    }

    @Override
    public Map<String, Object> preview(Long templateId) {
        MesLabelTemplate template = requireTemplate(templateId);
        String payload = renderService.render(template, sampleFields());
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("payload", payload);
        data.put("charset", renderService.charsetOf(template).name());
        data.put("lang", template.getLabelLang());
        data.put("templateRevision", template.getTemplateRevision());
        return data;
    }

    @Override
    public boolean testPrint(Long templateId) {
        return "SENT".equals(testPrintTask(templateId, null).getStatus());
    }

    /**
     * 测试打印先保存任务，再在事务外尝试发送，返回可查询的持久化任务回执。
     */
    @Override
    public LabelPrintTaskVo testPrintTask(Long templateId, String requestId) {
        MesLabelTemplate template = requireTemplate(templateId);
        Map<String, String> fields = sampleFields();
        String payload = renderService.render(template, fields);
        MesLabelPrintLog printLog =
                printService.newLog(null, null, template, MesLabelPrintLog.SOURCE_TEST);
        printService.freezeFields(printLog, fields);
        return printService.view(
                printService.submitAndSend(
                        printLog, payload, renderService.charsetOf(template), requestId));
    }

    /**
     * 历史二参数补打适配：能定位原任务时复用冻结指令，缺少原因的事实明确记录为兼容模式。
     */
    @Override
    public boolean reprint(Long snId, Long templateId) {
        throw new ServiceException("补打必须提供原打印任务 originalPrintLogId 和非空补打原因");
    }

    /**
     * 显式补打必须关联本 SN、本模板的原任务并填写原因；不得覆盖原任务审计。
     */
    @Override
    public boolean reprint(
            Long snId, Long templateId, Long originalPrintLogId, String reprintReason) {
        return "SENT"
                .equals(
                        reprintTask(snId, templateId, originalPrintLogId, reprintReason, null)
                                .getStatus());
    }

    /**
     * 显式补打以原任务及原因作为稳定业务意图，重复请求复用同一新任务。
     */
    @Override
    public LabelPrintTaskVo reprintTask(
            Long snId,
            Long templateId,
            Long originalPrintLogId,
            String reprintReason,
            String requestId) {
        if (originalPrintLogId == null) {
            throw new ServiceException("补打必须提供原打印任务 originalPrintLogId");
        }
        if (StringUtils.isBlank(reprintReason)) {
            throw new ServiceException("补打原因不能为空");
        }
        String reason = reprintReason.trim();
        if (reason.length() > 500) {
            throw new ServiceException("补打原因不能超过500个字符");
        }
        MesLabelPrintLog original = printService.findLog(originalPrintLogId);
        if (original == null) {
            throw new ServiceException("原打印任务不存在或无权访问: " + originalPrintLogId);
        }
        if (!Objects.equals(snId, original.getSnId())
                || !Objects.equals(templateId, original.getTemplateId())) {
            throw new ServiceException("原打印任务与本次 SN 或模板不匹配");
        }
        if ("PENDING".equals(original.getResult()) || "SENDING".equals(original.getResult())) {
            throw new ServiceException("原任务仍待发或发送中，不能另建补打任务");
        }
        return reprintAttempt(snId, templateId, original, reason, requestId);
    }

    /**
     * 优先使用原任务已冻结指令；只对缺少快照的历史记录按当前数据生成并明确标注。
     */
    private LabelPrintTaskVo reprintAttempt(
            Long snId,
            Long templateId,
            MesLabelPrintLog original,
            String reason,
            String requestId) {
        if (original != null
                && StringUtils.isNotBlank(original.getRenderedPayload())
                && StringUtils.isNotBlank(original.getPayloadCharset())) {
            MesLabelPrintLog attempt = printService.newReprintLog(original, reason, false);
            return printService.view(
                    printService.submitAndSend(
                            attempt,
                            original.getRenderedPayload(),
                            Charset.forName(original.getPayloadCharset()),
                            requestId));
        }
        if (original != null
                && ("FROZEN".equals(original.getSnapshotMode())
                        || StringUtils.isNotBlank(original.getTemplateSnapshot())
                        || StringUtils.isNotBlank(original.getFieldSnapshot())
                        || StringUtils.isNotBlank(original.getContentHash())
                        || StringUtils.isNotBlank(original.getPayloadHash()))) {
            throw new ServiceException("原任务未形成完整可发送的冻结指令，不能用当前模板替代");
        }
        ProSn sn = proSnMapper.selectById(snId);
        if (sn == null) {
            throw new ServiceException("SN不存在: " + snId);
        }
        MesLabelTemplate template = requireTemplate(templateId);
        Map<String, String> fields = renderService.snFields(sn);
        String payload = renderService.render(template, fields);
        MesLabelPrintLog attempt =
                printService.newLog(
                        sn.getSnId(),
                        fields.get("snCode"),
                        template,
                        MesLabelPrintLog.SOURCE_REPRINT);
        printService.freezeFields(attempt, fields);
        attempt.setOriginalPrintLogId(original == null ? null : original.getLogId());
        attempt.setReprintReason(reason);
        attempt.setSnapshotMode("LEGACY_RECORD");
        attempt.setRemark("原任务为历史记录且缺少冻结内容；本次按当前模板与SN生成，不能证明与原标签一致");
        return printService.view(
                printService.submitAndSend(
                        attempt, payload, renderService.charsetOf(template), requestId));
    }

    /**
     * 任务状态查询与原打印权限、当前租户范围一致。
     */
    @Override
    public LabelPrintTaskVo printTask(Long taskId) {
        return printService.taskInfo(taskId);
    }

    // ==================== 辅助 ====================

    private MesLabelTemplate requireTemplate(Long templateId) {
        MesLabelTemplate template = baseMapper.selectById(templateId);
        if (template == null) {
            throw new ServiceException("标签模板不存在: " + templateId);
        }
        return template;
    }

    private void applyDefaults(MesLabelTemplate template) {
        if (StringUtils.isBlank(template.getLabelType())) {
            template.setLabelType(LabelRenderService.TYPE_SN);
        }
        if (template.getPrinterPort() == null) {
            template.setPrinterPort(DEFAULT_PORT);
        }
        if (StringUtils.isBlank(template.getEnabled())) {
            template.setEnabled("Y");
        }
    }

    /**
     * 增改时用样例字段预渲染一遍, 保证 content JSON 结构合法(防脏模板上线)
     */
    private void validateRenderable(MesLabelTemplate template) {
        renderService.render(template, sampleFields());
    }

    private void checkCodeUnique(MesLabelTemplate template) {
        Long count =
                baseMapper.selectCount(
                        Wrappers.<MesLabelTemplate>lambdaQuery()
                                .eq(MesLabelTemplate::getTemplateCode, template.getTemplateCode())
                                .ne(
                                        template.getTemplateId() != null,
                                        MesLabelTemplate::getTemplateId,
                                        template.getTemplateId()));
        if (count != null && count > 0) {
            throw new ServiceException("模板编码已存在: " + template.getTemplateCode());
        }
    }

    /**
     * 样例字段域(预览/测试打印/增改校验共用)
     */
    private Map<String, String> sampleFields() {
        Map<String, String> fields = new LinkedHashMap<>(8);
        fields.put("snCode", "SN-FC-TEST-0001");
        fields.put("materialCode", "GW-1000-TEST");
        fields.put("materialName", "测试物料");
        fields.put("woNo", "MO-TEST-001");
        fields.put("cardNo", "FC-TEST-001");
        fields.put("productionDate", String.format("%tF", new Date()));
        return fields;
    }

    private LambdaQueryWrapper<MesLabelTemplate> buildQueryWrapper(MesLabelTemplateBo bo) {
        return Wrappers.<MesLabelTemplate>lambdaQuery()
                .eq(
                        StringUtils.isNotBlank(bo.getTemplateCode()),
                        MesLabelTemplate::getTemplateCode,
                        bo.getTemplateCode())
                .like(
                        StringUtils.isNotBlank(bo.getTemplateName()),
                        MesLabelTemplate::getTemplateName,
                        bo.getTemplateName())
                .eq(
                        StringUtils.isNotBlank(bo.getLabelType()),
                        MesLabelTemplate::getLabelType,
                        bo.getLabelType())
                .eq(
                        StringUtils.isNotBlank(bo.getLabelLang()),
                        MesLabelTemplate::getLabelLang,
                        bo.getLabelLang())
                .eq(
                        StringUtils.isNotBlank(bo.getEnabled()),
                        MesLabelTemplate::getEnabled,
                        bo.getEnabled())
                .orderByDesc(MesLabelTemplate::getCreateTime);
    }
}
