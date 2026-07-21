package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.bd.domain.BdBomAlternative;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomAlternativeBo;
import com.nexus.erp.bd.domain.vo.BdBomAlternativeVo;
import com.nexus.erp.bd.mapper.BdBomAlternativeMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.IBdBomAlternativeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BOM替代料Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdBomAlternativeServiceImpl implements IBdBomAlternativeService {

    private final BdBomAlternativeMapper baseMapper;
    private final BdMaterialMapper materialMapper;
    /**
     * 根据BOM明细ID查询BOM替代料列表
     *
     * @param detailId BOM明细ID
     * @return 结果列表
     */

    @Override
    public List<BdBomAlternativeVo> listByDetailId(Long detailId) {
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getDetailId, detailId)
                .orderByAsc(BdBomAlternative::getPriority));
    }
    /**
     * 根据BOM ID查询BOM替代料列表
     *
     * @param bomId BOM ID
     * @return 结果列表
     */

    @Override
    public List<BdBomAlternativeVo> listByBomId(Long bomId) {
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getBomId, bomId)
                .orderByAsc(BdBomAlternative::getPriority));
    }
    /**
     * 批量保存BOM替代料（用于子表明细的批量新增/更新）
     *
     * @param detailId BOM明细ID
     * @param bomId BOM ID
     * @param list 批量数据列表
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveBatch(Long detailId, Long bomId, List<BdBomAlternativeBo> list) {
        // 先删除该明细行的现有替代料
        baseMapper.delete(Wrappers.lambdaQuery(BdBomAlternative.class)
            .eq(BdBomAlternative::getDetailId, detailId));
        // 批量插入
        if (list == null || list.isEmpty()) {
            return 0;
        }
        List<BdBomAlternative> entities = list.stream().map(bo -> {
            BdBomAlternative entity = MapstructUtils.convert(bo, BdBomAlternative.class);
            entity.setAlternativeId(null);
            entity.setDetailId(detailId);
            entity.setBomId(bomId);
            return entity;
        }).collect(Collectors.toList());
        entities.forEach(baseMapper::insert);
        return entities.size();
    }
    /**
     * 批量删除BOM替代料
     *
     * @param ids 主键ID数组（支持批量）
     * @return 操作结果/影响行数
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }
    /**
     * 查询指定明细可用的BOM替代料
     *
     * @param detailId BOM明细ID
     * @return 结果列表
     */

    @Override
    public List<BdBomAlternativeVo> getAvailableAlternatives(Long detailId) {
        LocalDate today = LocalDate.now();
        return baseMapper.selectVoList(
            Wrappers.lambdaQuery(BdBomAlternative.class)
                .eq(BdBomAlternative::getDetailId, detailId)
                .eq(BdBomAlternative::getStatus, 1)
                .and(w -> w.isNull(BdBomAlternative::getEffectiveDate)
                    .or()
                    .le(BdBomAlternative::getEffectiveDate, today))
                .and(w -> w.isNull(BdBomAlternative::getExpiryDate)
                    .or()
                    .ge(BdBomAlternative::getExpiryDate, today))
                .orderByAsc(BdBomAlternative::getPriority));
    }
}
