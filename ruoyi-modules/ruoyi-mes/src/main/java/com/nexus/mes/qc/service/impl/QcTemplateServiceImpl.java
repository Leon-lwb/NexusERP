package com.nexus.mes.qc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.QcInspectItem;
import com.nexus.mes.qc.domain.QcInspectTemplate;
import com.nexus.mes.qc.domain.bo.QcInspectItemBo;
import com.nexus.mes.qc.domain.bo.QcInspectTemplateBo;
import com.nexus.mes.qc.domain.vo.QcInspectItemVo;
import com.nexus.mes.qc.domain.vo.QcInspectTemplateVo;
import com.nexus.mes.qc.mapper.QcInspectDetailMapper;
import com.nexus.mes.qc.mapper.QcInspectItemMapper;
import com.nexus.mes.qc.mapper.QcInspectTemplateMapper;
import com.nexus.mes.qc.service.IQcTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * QC检验模板 Service 实现
 * <p>模板(头) + 检验项目(子表) 主子表维护, 项目随模板整体保存(先删后插)。
 * 检验单明细(qc_inspect_detail)按模板快照生成后即与模板解耦。</p>
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class QcTemplateServiceImpl implements IQcTemplateService {

    /** 状态: 草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态: 启用 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final QcInspectTemplateMapper baseMapper;
    private final QcInspectItemMapper itemMapper;
    private final QcInspectDetailMapper detailMapper;

    @Override
    public TableDataInfo<QcInspectTemplateVo> selectPageList(QcInspectTemplateBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcInspectTemplate> lqw = buildQueryWrapper(bo);
        Page<QcInspectTemplateVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public QcInspectTemplateVo selectById(Long templateId) {
        QcInspectTemplateVo vo = baseMapper.selectVoById(templateId);
        if (vo != null) {
            vo.setItems(itemMapper.selectVoList(Wrappers.lambdaQuery(QcInspectItem.class)
                .eq(QcInspectItem::getTemplateId, templateId)
                .orderByAsc(QcInspectItem::getSeqNum)));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(QcInspectTemplateBo bo) {
        validateItems(bo.getItems());
        QcInspectTemplate entity = MapstructUtils.convert(bo, QcInspectTemplate.class);
        // MES-QC-09: 状态由服务端强制为草稿, 忽略入参 —— 新建模板不得直接落 ACTIVE 绕过启用校验
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersionNo() == null) {
            entity.setVersionNo(1);
        }
        int rows = baseMapper.insert(entity);
        saveItems(entity.getTemplateId(), bo.getItems());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(QcInspectTemplateBo bo) {
        validateItems(bo.getItems());
        QcInspectTemplate exist = baseMapper.selectById(bo.getTemplateId());
        if (exist == null) {
            throw new ServiceException("检验模板不存在");
        }
        QcInspectTemplate entity = MapstructUtils.convert(bo, QcInspectTemplate.class);
        // MES-QC-09: 状态由服务端强制为草稿, 忽略入参 —— 编辑不得把模板改成 ACTIVE 绕过 changeStatus 校验
        entity.setStatus(STATUS_DRAFT);
        int rows = baseMapper.updateById(entity);
        // 先删后插项目明细
        itemMapper.delete(Wrappers.lambdaQuery(QcInspectItem.class)
            .eq(QcInspectItem::getTemplateId, entity.getTemplateId()));
        if (bo.getItems() != null && !bo.getItems().isEmpty()) {
            saveItems(entity.getTemplateId(), bo.getItems());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        List<Long> idList = List.of(ids);
        // 校验: 已生成检验明细的模板不可删除(保证快照可溯源)
        Long ref = detailMapper.selectCount(Wrappers.lambdaQuery(QcInspectDetail.class)
            .in(QcInspectDetail::getTemplateId, idList));
        if (ref != null && ref > 0) {
            throw new ServiceException("所选模板已被" + ref + "条检验明细引用，无法删除；可停用代替。");
        }
        itemMapper.delete(Wrappers.lambdaQuery(QcInspectItem.class)
            .in(QcInspectItem::getTemplateId, idList));
        return baseMapper.deleteBatchIds(idList);
    }

    @Override
    public void changeStatus(Long templateId, boolean active) {
        QcInspectTemplate template = baseMapper.selectById(templateId);
        if (template == null) {
            throw new ServiceException("检验模板不存在");
        }
        if (active) {
            // 启用前校验: 必须有启用的检验项目
            Long enabled = itemMapper.selectCount(Wrappers.lambdaQuery(QcInspectItem.class)
                .eq(QcInspectItem::getTemplateId, templateId)
                .eq(QcInspectItem::getEnabled, "1"));
            if (enabled == null || enabled == 0) {
                throw new ServiceException("模板没有启用的检验项目，无法启用");
            }
            // MES-QC-09: 条件更新(CAS) —— 仅 DRAFT 可启用, 影响 0 行说明状态已被并发变更
            int rows = baseMapper.update(null, Wrappers.<QcInspectTemplate>lambdaUpdate()
                .set(QcInspectTemplate::getStatus, STATUS_ACTIVE)
                .eq(QcInspectTemplate::getTemplateId, templateId)
                .eq(QcInspectTemplate::getStatus, STATUS_DRAFT));
            if (rows == 0) {
                throw new ServiceException("模板状态已变更(仅草稿状态可启用)，请刷新后重试");
            }
            log.info("检验模板启用: templateId={}", templateId);
        } else {
            template.setStatus(STATUS_DRAFT);
            baseMapper.updateById(template);
            log.info("检验模板停用(转草稿): templateId={}", templateId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyVersion(Long templateId) {
        QcInspectTemplate source = baseMapper.selectById(templateId);
        if (source == null) {
            throw new ServiceException("检验模板不存在");
        }
        List<QcInspectItem> items = itemMapper.selectList(Wrappers.lambdaQuery(QcInspectItem.class)
            .eq(QcInspectItem::getTemplateId, templateId)
            .orderByAsc(QcInspectItem::getSeqNum));

        // 新版本: 编码加 -V{版本} 后缀, 版本号+1, 状态回到草稿
        int newVersion = (source.getVersionNo() == null ? 1 : source.getVersionNo()) + 1;
        QcInspectTemplate copy = new QcInspectTemplate();
        copy.setTemplateCode(source.getTemplateCode() + "-V" + newVersion);
        copy.setTemplateName(source.getTemplateName());
        copy.setTemplateType(source.getTemplateType());
        copy.setMaterialId(source.getMaterialId());
        copy.setMaterialCode(source.getMaterialCode());
        copy.setMaterialName(source.getMaterialName());
        copy.setVersionNo(newVersion);
        copy.setStatus(STATUS_DRAFT);
        copy.setRemark(source.getRemark());
        baseMapper.insert(copy);

        for (QcInspectItem item : items) {
            QcInspectItem itemCopy = new QcInspectItem();
            itemCopy.setTemplateId(copy.getTemplateId());
            itemCopy.setItemCode(item.getItemCode());
            itemCopy.setItemName(item.getItemName());
            itemCopy.setDataType(item.getDataType());
            itemCopy.setUnit(item.getUnit());
            itemCopy.setStandardType(item.getStandardType());
            itemCopy.setNominalValue(item.getNominalValue());
            itemCopy.setLslValue(item.getLslValue());
            itemCopy.setUslValue(item.getUslValue());
            itemCopy.setTolUpper(item.getTolUpper());
            itemCopy.setTolLower(item.getTolLower());
            itemCopy.setAqlLevel(item.getAqlLevel());
            itemCopy.setSamplePlanCode(item.getSamplePlanCode());
            itemCopy.setCriticality(item.getCriticality());
            itemCopy.setSeqNum(item.getSeqNum());
            itemCopy.setEnabled(item.getEnabled());
            itemCopy.setRemark(item.getRemark());
            itemMapper.insert(itemCopy);
        }
        log.info("检验模板版本复制: {}(V{}) -> {}(V{}), 项目数={}",
            source.getTemplateCode(), source.getVersionNo(), copy.getTemplateCode(), newVersion, items.size());
        return copy.getTemplateId();
    }

    // ============ private helpers ============

    /**
     * 校验项目明细: 编码唯一 + 定量项目必须给足判定所需标准
     */
    private void validateItems(List<QcInspectItemBo> items) {
        if (items == null || items.isEmpty()) {
            throw new ServiceException("检验模板至少需要一个检验项目");
        }
        for (int i = 0; i < items.size(); i++) {
            QcInspectItemBo item = items.get(i);
            String prefix = "第" + (i + 1) + "行项目: ";
            if (item.getEnabled() != null && "0".equals(item.getEnabled())) {
                continue;
            }
            String dataType = item.getDataType() == null ? "QUANTITATIVE" : item.getDataType();
            if ("QUANTITATIVE".equals(dataType)) {
                String standardType = item.getStandardType();
                if (standardType == null || standardType.isBlank()) {
                    throw new ServiceException(prefix + "定量项目必须指定标准类型(NOMINAL/LSL_ONLY/USL_ONLY/LSL_USL)");
                }
                switch (standardType) {
                    case "LSL_USL" -> {
                        requireValue(item.getLslValue(), prefix + "LSL_USL 需要下限");
                        requireValue(item.getUslValue(), prefix + "LSL_USL 需要上限");
                    }
                    case "LSL_ONLY" -> requireValue(item.getLslValue(), prefix + "LSL_ONLY 需要下限");
                    case "USL_ONLY" -> requireValue(item.getUslValue(), prefix + "USL_ONLY 需要上限");
                    case "NOMINAL" -> {
                        requireValue(item.getNominalValue(), prefix + "NOMINAL 需要名义值");
                        requireValue(item.getTolUpper(), prefix + "NOMINAL 需要上公差");
                        requireValue(item.getTolLower(), prefix + "NOMINAL 需要下公差");
                        // MES-QC-06: 公差必须为正 —— 负公差会被判定引擎按"名义值 ± 负数"反向收窄区间, 属配置错误
                        if (item.getTolUpper().signum() < 0 || item.getTolLower().signum() < 0) {
                            throw new ServiceException(prefix + "上/下公差请填正值（下公差按名义值减）");
                        }
                    }
                    default -> throw new ServiceException(prefix + "未知标准类型: " + standardType);
                }
            }
        }
        // MES-QC-09: 至少一个启用项目, 否则模板启用后无任何可检验内容
        boolean anyEnabled = items.stream()
            .anyMatch(i -> i.getEnabled() == null || !"0".equals(i.getEnabled()));
        if (!anyEnabled) {
            throw new ServiceException("检验模板至少需要一个启用的检验项目");
        }
        long distinct = items.stream().map(QcInspectItemBo::getItemCode).filter(Objects::nonNull).distinct().count();
        if (distinct != items.size()) {
            throw new ServiceException("检验项目编码存在重复");
        }
    }

    private void requireValue(Object value, String message) {
        if (value == null) {
            throw new ServiceException(message);
        }
    }

    /**
     * 保存项目明细(先删后插模式调用)
     */
    private void saveItems(Long templateId, List<QcInspectItemBo> itemBos) {
        for (int i = 0; i < itemBos.size(); i++) {
            QcInspectItemBo itemBo = itemBos.get(i);
            QcInspectItem entity = MapstructUtils.convert(itemBo, QcInspectItem.class);
            entity.setItemId(null);
            entity.setTemplateId(templateId);
            if (entity.getSeqNum() == null) {
                entity.setSeqNum(i + 1);
            }
            if (entity.getDataType() == null) {
                entity.setDataType("QUANTITATIVE");
            }
            if (entity.getCriticality() == null) {
                entity.setCriticality("MAJOR");
            }
            if (entity.getEnabled() == null) {
                entity.setEnabled("1");
            }
            itemMapper.insert(entity);
        }
    }

    private LambdaQueryWrapper<QcInspectTemplate> buildQueryWrapper(QcInspectTemplateBo bo) {
        LambdaQueryWrapper<QcInspectTemplate> lqw = Wrappers.lambdaQuery();
        lqw.like(bo.getTemplateCode() != null, QcInspectTemplate::getTemplateCode, bo.getTemplateCode());
        lqw.like(bo.getTemplateName() != null, QcInspectTemplate::getTemplateName, bo.getTemplateName());
        lqw.eq(bo.getTemplateType() != null, QcInspectTemplate::getTemplateType, bo.getTemplateType());
        lqw.eq(bo.getMaterialId() != null, QcInspectTemplate::getMaterialId, bo.getMaterialId());
        lqw.eq(bo.getStatus() != null, QcInspectTemplate::getStatus, bo.getStatus());
        lqw.orderByDesc(QcInspectTemplate::getCreateTime);
        return lqw;
    }
}
