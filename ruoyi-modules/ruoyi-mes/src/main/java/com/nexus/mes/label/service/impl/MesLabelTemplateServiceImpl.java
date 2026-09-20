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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public TableDataInfo<MesLabelTemplateVo> selectPageList(MesLabelTemplateBo bo, PageQuery pageQuery) {
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
        return baseMapper.updateById(template) > 0;
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
        return data;
    }

    @Override
    public boolean testPrint(Long templateId) {
        MesLabelTemplate template = requireTemplate(templateId);
        String payload = renderService.render(template, sampleFields());
        // MES-LABEL-02: 测试打印同样留痕(print_source=TEST), 便于排查"打印了但没出纸"
        MesLabelPrintLog printLog = printService.newLog(null, null, template, MesLabelPrintLog.SOURCE_TEST);
        return printService.send(printLog, payload, renderService.charsetOf(template));
    }

    /**
     * 人工补打(MES-LABEL-02): SN + 模板重新渲染直发, 留痕 print_source=REPRINT(重打计数可查)
     */
    @Override
    public boolean reprint(Long snId, Long templateId) {
        ProSn sn = proSnMapper.selectById(snId);
        if (sn == null) {
            throw new ServiceException("SN不存在: " + snId);
        }
        MesLabelTemplate template = requireTemplate(templateId);
        String payload = renderService.render(template, renderService.snFields(sn));
        MesLabelPrintLog printLog = printService.newLog(sn.getSnId(), sn.getSnCode(), template,
            MesLabelPrintLog.SOURCE_REPRINT);
        return printService.send(printLog, payload, renderService.charsetOf(template));
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

    /** 增改时用样例字段预渲染一遍, 保证 content JSON 结构合法(防脏模板上线) */
    private void validateRenderable(MesLabelTemplate template) {
        renderService.render(template, sampleFields());
    }

    private void checkCodeUnique(MesLabelTemplate template) {
        Long count = baseMapper.selectCount(Wrappers.<MesLabelTemplate>lambdaQuery()
            .eq(MesLabelTemplate::getTemplateCode, template.getTemplateCode())
            .ne(template.getTemplateId() != null, MesLabelTemplate::getTemplateId, template.getTemplateId()));
        if (count != null && count > 0) {
            throw new ServiceException("模板编码已存在: " + template.getTemplateCode());
        }
    }

    /** 样例字段域(预览/测试打印/增改校验共用) */
    private Map<String, String> sampleFields() {
        Map<String, String> fields = new LinkedHashMap<>(8);
        fields.put("snCode", "SN-FC-TEST-0001");
        fields.put("materialCode", "GW-1000-TEST");
        fields.put("materialName", "测试物料");
        fields.put("woNo", "MO-TEST-001");
        fields.put("cardNo", "FC-TEST-001");
        fields.put("productionDate", String.format("%tF", new java.util.Date()));
        return fields;
    }

    private LambdaQueryWrapper<MesLabelTemplate> buildQueryWrapper(MesLabelTemplateBo bo) {
        return Wrappers.<MesLabelTemplate>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getTemplateCode()), MesLabelTemplate::getTemplateCode, bo.getTemplateCode())
            .like(StringUtils.isNotBlank(bo.getTemplateName()), MesLabelTemplate::getTemplateName, bo.getTemplateName())
            .eq(StringUtils.isNotBlank(bo.getLabelType()), MesLabelTemplate::getLabelType, bo.getLabelType())
            .eq(StringUtils.isNotBlank(bo.getLabelLang()), MesLabelTemplate::getLabelLang, bo.getLabelLang())
            .eq(StringUtils.isNotBlank(bo.getEnabled()), MesLabelTemplate::getEnabled, bo.getEnabled())
            .orderByDesc(MesLabelTemplate::getCreateTime);
    }
}
