package com.nexus.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.erp.so.mapper.SoContractLineMapper;
import com.nexus.erp.so.mapper.SoContractMapper;
import com.nexus.erp.so.service.ISoContractService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 销售合同Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoContractServiceImpl implements ISoContractService {

    private final SoContractMapper baseMapper;
    private final SoContractLineMapper lineMapper;

    /**
     * 分页查询销售合同列表
     */
    @Override
    public TableDataInfo<SoContractVo> selectPageList(SoContractBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoContract> lqw = buildQueryWrapper(bo);
        Page<SoContractVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售合同列表（不分页）
     */
    @Override
    public List<SoContractVo> selectList(SoContractBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售合同（含明细行）
     */
    @Override
    public SoContractVo selectById(Long id) {
        SoContractVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoContractLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoContractLine::getContractId, vo.getContractId());
            lqw.orderByAsc(SoContractLine::getSortOrder);
            vo.setSoContractLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增销售合同（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoContractBo bo) {
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        int rows = baseMapper.insert(entity);
        // 插入明细行
        if (bo.getSoContractLineList() != null && !bo.getSoContractLineList().isEmpty()) {
            for (SoContractLine line : bo.getSoContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改销售合同（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoContractBo bo) {
        SoContract entity = MapstructUtils.convert(bo, SoContract.class);
        int rows = baseMapper.updateById(entity);
        // 删除原有明细行并重新插入
        if (bo.getSoContractLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, entity.getContractId()));
            for (SoContractLine line : bo.getSoContractLineList()) {
                line.setContractId(entity.getContractId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除销售合同（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            lineMapper.delete(Wrappers.lambdaQuery(SoContractLine.class)
                .eq(SoContractLine::getContractId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoContract> buildQueryWrapper(SoContractBo bo) {
        LambdaQueryWrapper<SoContract> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContractNo()), SoContract::getContractNo, bo.getContractNo());
        lqw.like(StringUtils.isNotBlank(bo.getContractName()), SoContract::getContractName, bo.getContractName());
        lqw.eq(bo.getClientId() != null, SoContract::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoContract::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoContract::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getContractType()), SoContract::getContractType, bo.getContractType());
        lqw.orderByDesc(SoContract::getCreateTime);
        return lqw;
    }
}
