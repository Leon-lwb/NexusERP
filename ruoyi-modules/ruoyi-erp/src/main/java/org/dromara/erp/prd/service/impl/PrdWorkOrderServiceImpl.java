package org.dromara.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdWorkOrder;
import org.dromara.erp.prd.domain.bo.PrdWorkOrderBo;
import org.dromara.erp.prd.domain.vo.PrdWorkOrderVo;
import org.dromara.erp.prd.mapper.PrdWorkOrderMapper;
import org.dromara.erp.prd.service.IPrdWorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 生产工单Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class PrdWorkOrderServiceImpl implements IPrdWorkOrderService {

    private final PrdWorkOrderMapper baseMapper;

    @Override
    public TableDataInfo<PrdWorkOrderVo> selectPageList(PrdWorkOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildQueryWrapper(bo);
        Page<PrdWorkOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdWorkOrderVo> selectList(PrdWorkOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdWorkOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public PrdWorkOrder selectPrdWorkOrderByWoId(Long woId) {
        return baseMapper.selectById(woId);
    }

    @Override
    public List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = buildEntityQueryWrapper(prdWorkOrder);
        return baseMapper.selectList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.insert(prdWorkOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkOrderBo bo) {
        PrdWorkOrder entity = MapstructUtils.convert(bo, PrdWorkOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder) {
        return baseMapper.updateById(prdWorkOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoIds(Long[] woIds) {
        return baseMapper.deleteBatchIds(List.of(woIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkOrderByWoId(Long woId) {
        return baseMapper.deleteById(woId);
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildQueryWrapper(PrdWorkOrderBo bo) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), PrdWorkOrder::getWoNo, bo.getWoNo());
        lqw.like(StringUtils.isNotBlank(bo.getPlanName()), PrdWorkOrder::getPlanName, bo.getPlanName());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceType()), PrdWorkOrder::getSourceType, bo.getSourceType());
        lqw.eq(bo.getSourceId() != null, PrdWorkOrder::getSourceId, bo.getSourceId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceNo()), PrdWorkOrder::getSourceNo, bo.getSourceNo());
        lqw.eq(bo.getSourceLineId() != null, PrdWorkOrder::getSourceLineId, bo.getSourceLineId());
        lqw.eq(bo.getMaterialId() != null, PrdWorkOrder::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdWorkOrder::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getWoStatus()), PrdWorkOrder::getWoStatus, bo.getWoStatus());
        lqw.eq(bo.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, bo.getWorkshopId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkshopName()), PrdWorkOrder::getWorkshopName, bo.getWorkshopName());
        lqw.eq(bo.getWorkstationId() != null, PrdWorkOrder::getWorkstationId, bo.getWorkstationId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkstationName()), PrdWorkOrder::getWorkstationName, bo.getWorkstationName());
        lqw.eq(bo.getProductionLineId() != null, PrdWorkOrder::getProductionLineId, bo.getProductionLineId());
        lqw.eq(bo.getResponsibleId() != null, PrdWorkOrder::getResponsibleId, bo.getResponsibleId());
        lqw.like(StringUtils.isNotBlank(bo.getResponsibleName()), PrdWorkOrder::getResponsibleName, bo.getResponsibleName());
        lqw.eq(bo.getTeamId() != null, PrdWorkOrder::getTeamId, bo.getTeamId());
        lqw.like(StringUtils.isNotBlank(bo.getTeamName()), PrdWorkOrder::getTeamName, bo.getTeamName());
        lqw.eq(bo.getPriority() != null, PrdWorkOrder::getPriority, bo.getPriority());
        return lqw;
    }

    private LambdaQueryWrapper<PrdWorkOrder> buildEntityQueryWrapper(PrdWorkOrder entity) {
        LambdaQueryWrapper<PrdWorkOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getWoNo()), PrdWorkOrder::getWoNo, entity.getWoNo());
        lqw.like(StringUtils.isNotBlank(entity.getPlanName()), PrdWorkOrder::getPlanName, entity.getPlanName());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceType()), PrdWorkOrder::getSourceType, entity.getSourceType());
        lqw.eq(entity.getSourceId() != null, PrdWorkOrder::getSourceId, entity.getSourceId());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceNo()), PrdWorkOrder::getSourceNo, entity.getSourceNo());
        lqw.eq(entity.getSourceLineId() != null, PrdWorkOrder::getSourceLineId, entity.getSourceLineId());
        lqw.eq(entity.getMaterialId() != null, PrdWorkOrder::getMaterialId, entity.getMaterialId());
        lqw.like(StringUtils.isNotBlank(entity.getMaterialName()), PrdWorkOrder::getMaterialName, entity.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(entity.getWoStatus()), PrdWorkOrder::getWoStatus, entity.getWoStatus());
        lqw.eq(entity.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, entity.getWorkshopId());
        lqw.like(StringUtils.isNotBlank(entity.getWorkshopName()), PrdWorkOrder::getWorkshopName, entity.getWorkshopName());
        lqw.eq(entity.getWorkstationId() != null, PrdWorkOrder::getWorkstationId, entity.getWorkstationId());
        lqw.like(StringUtils.isNotBlank(entity.getWorkstationName()), PrdWorkOrder::getWorkstationName, entity.getWorkstationName());
        lqw.eq(entity.getProductionLineId() != null, PrdWorkOrder::getProductionLineId, entity.getProductionLineId());
        lqw.eq(entity.getResponsibleId() != null, PrdWorkOrder::getResponsibleId, entity.getResponsibleId());
        lqw.like(StringUtils.isNotBlank(entity.getResponsibleName()), PrdWorkOrder::getResponsibleName, entity.getResponsibleName());
        lqw.eq(entity.getTeamId() != null, PrdWorkOrder::getTeamId, entity.getTeamId());
        lqw.like(StringUtils.isNotBlank(entity.getTeamName()), PrdWorkOrder::getTeamName, entity.getTeamName());
        lqw.eq(entity.getPriority() != null, PrdWorkOrder::getPriority, entity.getPriority());
        return lqw;
    }
}
