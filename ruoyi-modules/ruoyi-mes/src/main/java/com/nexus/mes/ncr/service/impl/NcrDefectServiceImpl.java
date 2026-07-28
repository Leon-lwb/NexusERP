package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.mes.ncr.domain.NcrDefect;
import com.nexus.mes.ncr.domain.bo.NcrDefectBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MES NCR缺陷明细 Service 业务层实现
 * <p>
 * 缺陷明细按 NCR 单ID 管理,支持批量创建(由 NcrOrderServiceImpl 内部调用)和补录。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ncrDefectService")
public class NcrDefectServiceImpl implements INcrDefectService {

    private final NcrDefectMapper baseMapper;

    /**
     * 查询缺陷明细列表(按 NCR 单ID)
     *
     * @param ncrId NCR单ID
     * @return 缺陷明细列表
     */
    @Override
    public List<NcrDefectVo> selectByNcrId(Long ncrId) {
        return baseMapper.selectVoList(
            Wrappers.<NcrDefect>lambdaQuery()
                .eq(NcrDefect::getNcrId, ncrId)
                .orderByAsc(NcrDefect::getCreateTime));
    }

    /**
     * 新增缺陷明细(补录)
     *
     * @param bo 缺陷明细业务对象
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(NcrDefectBo bo) {
        NcrDefect defect = new NcrDefect();
        defect.setNcrId(bo.getNcrId());
        defect.setDefectCodeId(bo.getDefectCodeId());
        defect.setDefectCode(bo.getDefectCode());
        defect.setDefectName(bo.getDefectName());
        defect.setDefectQty(bo.getDefectQty());
        defect.setDefectCause(bo.getDefectCause());
        defect.setRootCauseDesc(bo.getRootCauseDesc());
        int rows = baseMapper.insert(defect);
        log.info("新增缺陷明细: ncrId={}, defectCode={}", bo.getNcrId(), bo.getDefectCode());
        return rows > 0;
    }

    /**
     * 批量插入缺陷明细(供 NcrOrderServiceImpl.createNcr 内部调用)
     *
     * @param ncrId   NCR单ID
     * @param defects 缺陷明细列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchInsertForNcr(Long ncrId, List<NcrDefect> defects) {
        if (defects == null || defects.isEmpty()) {
            return;
        }
        for (NcrDefect defect : defects) {
            if (defect.getNcrId() == null) {
                defect.setNcrId(ncrId);
            }
            baseMapper.insert(defect);
        }
        log.info("批量插入缺陷明细: ncrId={}, count={}", ncrId, defects.size());
    }
}