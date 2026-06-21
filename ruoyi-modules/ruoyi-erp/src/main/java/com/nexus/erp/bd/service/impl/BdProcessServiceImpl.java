package com.nexus.erp.bd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdProcess;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.bo.BdProcessBo;
import com.nexus.erp.bd.domain.vo.BdProcessVo;
import com.nexus.erp.bd.mapper.BdProcessMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.service.IBdProcessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工序定义Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class BdProcessServiceImpl implements IBdProcessService {

    private final BdProcessMapper baseMapper;
    private final BdRoutingStepMapper routingStepMapper;

    @Override
    public TableDataInfo<BdProcessVo> selectPageList(BdProcessBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<BdProcess> lqw = buildQueryWrapper(bo);
        Page<BdProcessVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<BdProcessVo> selectList(BdProcessBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public BdProcessVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(BdProcessBo bo) {
        BdProcess entity = MapstructUtils.convert(bo, BdProcess.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(BdProcessBo bo) {
        BdProcess entity = MapstructUtils.convert(bo, BdProcess.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 校验是否有工艺路线步骤引用这些工序
        Long ref = routingStepMapper.selectCount(
            Wrappers.lambdaQuery(BdRoutingStep.class)
                .in(BdRoutingStep::getProcessId, List.of(ids)));
        if (ref != null && ref > 0) {
            throw new ServiceException("所选工序已被" + ref + "条工艺路线步骤引用，无法删除");
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<BdProcess> buildQueryWrapper(BdProcessBo bo) {
        LambdaQueryWrapper<BdProcess> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getProcessCode()), BdProcess::getProcessCode, bo.getProcessCode());
        lqw.like(StringUtils.isNotBlank(bo.getProcessName()), BdProcess::getProcessName, bo.getProcessName());
        lqw.eq(StringUtils.isNotBlank(bo.getProcessCategory()), BdProcess::getProcessCategory, bo.getProcessCategory());
        lqw.eq(bo.getWorkstationId() != null, BdProcess::getWorkstationId, bo.getWorkstationId());
        lqw.eq(bo.getIsKeyProcess() != null, BdProcess::getIsKeyProcess, bo.getIsKeyProcess());
        lqw.eq(bo.getIsOutsource() != null, BdProcess::getIsOutsource, bo.getIsOutsource());
        lqw.eq(bo.getStatus() != null, BdProcess::getStatus, bo.getStatus());
        return lqw;
    }
}
