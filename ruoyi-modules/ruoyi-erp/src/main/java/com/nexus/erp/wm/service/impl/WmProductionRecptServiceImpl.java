package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IWmProductionRecptService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionRecptServiceImpl implements IWmProductionRecptService {

    private final WmProductionRecptMapper baseMapper;
    private final IWmStockOperationService stockOperationService;

    @Override
    public TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = buildQueryWrapper(bo);
        Page<WmProductionRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmProductionRecptVo> selectList(WmProductionRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmProductionRecptVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionRecptBo bo) {
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        if (entity.getStatus() == null) {
            entity.setStatus("DRAFT");
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionRecptBo bo) {
        WmProductionRecpt entity = MapstructUtils.convert(bo, WmProductionRecpt.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long recptId) {
        WmProductionRecpt recpt = baseMapper.selectById(recptId);
        if (recpt == null) {
            throw new ServiceException("生产入库单不存在");
        }
        if ("EXECUTED".equals(recpt.getStatus())) {
            throw new ServiceException("生产入库单已执行");
        }
        if (recpt.getQuantity() == null || recpt.getQuantity().signum() <= 0) {
            throw new ServiceException("入库数量必须大于0");
        }

        stockOperationService.postProductionCompletion(
            recpt.getWoId(),
            recpt.getMaterialId(),
            recpt.getMaterialCode(),
            recpt.getMaterialName(),
            recpt.getQuantity(),
            recpt.getWarehouseId(),
            recpt.getLocationId(),
            new Date()
        );

        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("生产入库单执行完成, recptId={}", recptId);
    }

    private LambdaQueryWrapper<WmProductionRecpt> buildQueryWrapper(WmProductionRecptBo bo) {
        LambdaQueryWrapper<WmProductionRecpt> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRecptCode()), WmProductionRecpt::getRecptCode, bo.getRecptCode());
        lqw.eq(bo.getWoId() != null, WmProductionRecpt::getWoId, bo.getWoId());
        lqw.like(StringUtils.isNotBlank(bo.getWoNo()), WmProductionRecpt::getWoNo, bo.getWoNo());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionRecpt::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionRecpt::getCreateTime);
        return lqw;
    }
}
