package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.bo.ProSnBo;
import com.nexus.mes.pro.domain.vo.ProFlowCardStepVo;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.mapper.ProFlowCardMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.service.IProSnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES SN序列号 Service 业务层实现
 * <p>
 * 状态机: IN_PRODUCTION -> COMPLETED -> SHIPPED
 *                          └-> SCRAPPED (终态)
 *          COMPLETED -> SCRAPPED
 * 追溯: 前向(SN->流转卡工序链->操作员 + 子SN) + 后向(沿 parent_sn_id 父SN链)
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("proSnService")
public class ProSnServiceImpl implements IProSnService {

    /** 后向追溯最大递归深度(防环) */
    private static final int MAX_PARENT_DEPTH = 10;

    private final ProSnMapper baseMapper;
    private final ProFlowCardMapper flowCardMapper;
    private final ProFlowCardStepMapper stepMapper;

    @Override
    public TableDataInfo<ProSnVo> selectPageList(ProSnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ProSn> lqw = buildQueryWrapper(bo);
        Page<ProSnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public ProSnVo selectById(Long snId) {
        return baseMapper.selectVoById(snId);
    }

    @Override
    public ProSnVo selectByCode(String snCode) {
        return baseMapper.selectVoOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, snCode));
    }

    /**
     * 从流转卡批量生成SN
     */
    @Lock4j(keys = {"#cardId"})
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProSnVo> generateSnForCard(Long cardId, Integer quantity, String initialStatus) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("生成数量必须大于0");
        }
        // 1. 查流转卡(校验存在), 取冗余字段
        ProFlowCard card = flowCardMapper.selectById(cardId);
        if (card == null) {
            throw new RuntimeException("流转卡不存在: " + cardId);
        }

        // 2. 计算起始序号(该卡已有SN数 + 1), @Lock4j 保证并发安全
        Long existingCount = baseMapper.selectCount(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getCardId, cardId));
        int startSeq = existingCount == null ? 1 : existingCount.intValue() + 1;

        String status = StringUtils.isNotBlank(initialStatus) ? initialStatus : STATUS_IN_PRODUCTION;
        LocalDate today = LocalDate.now();

        // 3. 循环生成
        List<ProSn> snList = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            ProSn sn = new ProSn();
            sn.setSnCode(generateSnCode(card.getCardNo(), startSeq + i));
            sn.setMesWoId(card.getMesWoId());
            sn.setWoNo(card.getWoNo());
            sn.setCardId(cardId);
            sn.setCardNo(card.getCardNo());
            sn.setMaterialId(card.getMaterialId());
            sn.setMaterialCode(card.getMaterialCode());
            sn.setMaterialName(card.getMaterialName());
            sn.setSnStatus(status);
            sn.setSource(SOURCE_BATCH);
            sn.setProductionDate(java.sql.Date.valueOf(today));
            snList.add(sn);
        }

        for (ProSn sn : snList) {
            baseMapper.insert(sn);
        }
        log.info("批量生成SN成功: cardId={}, quantity={}, startSeq={}", cardId, quantity, startSeq);

        // 4. 返回VO列表
        List<Long> ids = snList.stream().map(ProSn::getSnId).toList();
        return baseMapper.selectVoList(Wrappers.<ProSn>lambdaQuery()
            .in(ProSn::getSnId, ids)
            .orderByAsc(ProSn::getSnCode));
    }

    /**
     * 手动创建单个SN(可指定parentSnId用于组装追溯)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProSnVo createSn(Long mesWoId, Long cardId, Long materialId,
                            String snCode, Long parentSnId, String remark) {
        if (StringUtils.isBlank(snCode)) {
            throw new RuntimeException("SN编码不能为空");
        }
        // 1. 校验 sn_code 唯一
        Long existCount = baseMapper.selectCount(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, snCode));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("SN编码已存在: " + snCode);
        }

        String parentSnCode = null;
        if (parentSnId != null) {
            // 2. 校验父SN存在 + 防环(parentSnId 不能是当前SN的后代, 但此处新建无snId, 仅校验父SN存在)
            ProSn parent = baseMapper.selectById(parentSnId);
            if (parent == null) {
                throw new RuntimeException("父SN不存在: " + parentSnId);
            }
            parentSnCode = parent.getSnCode();
            // 冗余父SN的物料族校验(可选: 同物料才允许组装, 此处放宽为仅记录关联)
            if (materialId == null) {
                materialId = parent.getMaterialId();
            }
        }
        if (materialId == null) {
            throw new RuntimeException("物料ID不能为空");
        }

        // 3. 构造并插入
        ProSn sn = new ProSn();
        sn.setSnCode(snCode);
        sn.setMesWoId(mesWoId);
        sn.setCardId(cardId);
        sn.setMaterialId(materialId);
        sn.setSnStatus(STATUS_IN_PRODUCTION);
        sn.setParentSnId(parentSnId);
        sn.setParentSnCode(parentSnCode);
        sn.setSource(SOURCE_MANUAL);
        sn.setProductionDate(java.sql.Date.valueOf(LocalDate.now()));
        sn.setRemark(remark);
        baseMapper.insert(sn);

        log.info("手动创建SN成功: snCode={}, parentSnId={}", snCode, parentSnId);
        return baseMapper.selectVoById(sn.getSnId());
    }

    /**
     * 完工(IN_PRODUCTION -> COMPLETED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeSn(Long snId) {
        int rows = baseMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
            .set(ProSn::getSnStatus, STATUS_COMPLETED)
            .eq(ProSn::getSnId, snId)
            .eq(ProSn::getSnStatus, STATUS_IN_PRODUCTION));
        if (rows == 0) {
            throw new RuntimeException("SN状态已变更, 仅生产中可完工");
        }
        log.info("SN完工: snId={}", snId);
    }

    /**
     * 报废(IN_PRODUCTION/COMPLETED -> SCRAPPED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scrapSn(Long snId, String remark) {
        int rows = baseMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
            .set(ProSn::getSnStatus, STATUS_SCRAPPED)
            .set(StringUtils.isNotBlank(remark), ProSn::getRemark, remark)
            .eq(ProSn::getSnId, snId)
            .in(ProSn::getSnStatus, STATUS_IN_PRODUCTION, STATUS_COMPLETED));
        if (rows == 0) {
            throw new RuntimeException("SN状态已变更, 仅生产中或已完成可报废");
        }
        log.info("SN报废: snId={}", snId);
    }

    /**
     * 发货(COMPLETED -> SHIPPED)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipSn(Long snId) {
        int rows = baseMapper.update(null, Wrappers.<ProSn>lambdaUpdate()
            .set(ProSn::getSnStatus, STATUS_SHIPPED)
            .eq(ProSn::getSnId, snId)
            .eq(ProSn::getSnStatus, STATUS_COMPLETED));
        if (rows == 0) {
            throw new RuntimeException("SN状态已变更, 仅已完成可发货");
        }
        log.info("SN发货: snId={}", snId);
    }

    /**
     * 追溯: 前向(SN->工序->操作员 + 子SN) + 后向(父SN链)
     */
    @Override
    public Map<String, Object> trace(Long snId) {
        Map<String, Object> result = new HashMap<>();
        ProSnVo sn = baseMapper.selectVoById(snId);
        if (sn == null) {
            throw new RuntimeException("SN不存在: " + snId);
        }
        result.put("sn", sn);

        // 前向: 流转卡工序链(若SN关联了流转卡)
        List<Map<String, Object>> forwardSteps = new ArrayList<>();
        if (sn.getCardId() != null) {
            List<ProFlowCardStepVo> steps = stepMapper.selectVoList(Wrappers.<com.nexus.mes.pro.domain.ProFlowCardStep>lambdaQuery()
                .eq(com.nexus.mes.pro.domain.ProFlowCardStep::getCardId, sn.getCardId())
                .orderByAsc(com.nexus.mes.pro.domain.ProFlowCardStep::getSequenceNum));
            for (ProFlowCardStepVo step : steps) {
                Map<String, Object> m = new HashMap<>();
                m.put("stepRecordId", step.getStepRecordId());
                m.put("sequenceNum", step.getSequenceNum());
                m.put("processCode", step.getProcessCode());
                m.put("processName", step.getProcessName());
                m.put("workcenterCode", step.getWorkcenterCode());
                m.put("stepStatus", step.getStepStatus());
                m.put("operatorId", step.getOperatorId());
                m.put("operatorName", step.getOperatorName());
                m.put("goodQty", step.getGoodQty());
                m.put("defectQty", step.getDefectQty());
                m.put("scrapQty", step.getScrapQty());
                m.put("actualStartTime", step.getActualStartTime());
                m.put("actualEndTime", step.getActualEndTime());
                forwardSteps.add(m);
            }
        }
        result.put("forwardSteps", forwardSteps);

        // 前向: 子SN列表(当前SN作为父SN的下级组装件)
        List<ProSnVo> children = baseMapper.selectVoList(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getParentSnId, snId)
            .orderByAsc(ProSn::getSnCode));
        result.put("children", children);

        // 后向: 沿 parent_sn_id 向上递归父SN链(限深防环)
        List<ProSnVo> parentChain = new ArrayList<>();
        Long currentParentId = sn.getParentSnId();
        int depth = 0;
        while (currentParentId != null && depth < MAX_PARENT_DEPTH) {
            ProSnVo parent = baseMapper.selectVoById(currentParentId);
            if (parent == null) {
                break;
            }
            parentChain.add(parent);
            currentParentId = parent.getParentSnId();
            depth++;
        }
        result.put("parentChain", parentChain);

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成SN编码: SN + 流转卡编号 + 4位序号
     */
    private String generateSnCode(String cardNo, int seq) {
        return "SN-" + cardNo + "-" + String.format("%04d", seq);
    }

    private LambdaQueryWrapper<ProSn> buildQueryWrapper(ProSnBo bo) {
        return Wrappers.<ProSn>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getSnCode()), ProSn::getSnCode, bo.getSnCode())
            .eq(StringUtils.isNotBlank(bo.getSnStatus()), ProSn::getSnStatus, bo.getSnStatus())
            .eq(StringUtils.isNotBlank(bo.getSource()), ProSn::getSource, bo.getSource())
            .eq(bo.getMesWoId() != null, ProSn::getMesWoId, bo.getMesWoId())
            .eq(bo.getCardId() != null, ProSn::getCardId, bo.getCardId())
            .eq(bo.getMaterialId() != null, ProSn::getMaterialId, bo.getMaterialId())
            .eq(bo.getParentSnId() != null, ProSn::getParentSnId, bo.getParentSnId())
            .orderByDesc(ProSn::getCreateTime);
    }
}
