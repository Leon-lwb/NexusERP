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
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.mapper.SoInvoiceLineMapper;
import com.nexus.erp.so.mapper.SoInvoiceMapper;
import com.nexus.erp.so.service.ISoInvoiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 销售发票Service业务层处理
 *
 * @author nexus
 * @date 2026-06-14
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoInvoiceServiceImpl implements ISoInvoiceService {

    private final SoInvoiceMapper baseMapper;
    private final SoInvoiceLineMapper lineMapper;

    /**
     * 分页查询销售发票列表
     */
    @Override
    public TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoInvoice> lqw = buildQueryWrapper(bo);
        Page<SoInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售发票列表（不分页）
     */
    @Override
    public List<SoInvoiceVo> selectList(SoInvoiceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售发票（含明细行）
     */
    @Override
    public SoInvoiceVo selectById(Long id) {
        SoInvoiceVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            LambdaQueryWrapper<SoInvoiceLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoInvoiceLine::getInvoiceId, vo.getInvoiceId());
            lqw.orderByAsc(SoInvoiceLine::getSortOrder);
            vo.setSoInvoiceLineList(lineMapper.selectList(lqw));
        }
        return vo;
    }

    /**
     * 新增销售发票（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoInvoiceBo bo) {
        SoInvoice entity = MapstructUtils.convert(bo, SoInvoice.class);
        int rows = baseMapper.insert(entity);
        if (bo.getSoInvoiceLineList() != null && !bo.getSoInvoiceLineList().isEmpty()) {
            for (SoInvoiceLine line : bo.getSoInvoiceLineList()) {
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 修改销售发票（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoInvoiceBo bo) {
        SoInvoice entity = MapstructUtils.convert(bo, SoInvoice.class);
        int rows = baseMapper.updateById(entity);
        if (bo.getSoInvoiceLineList() != null) {
            lineMapper.delete(Wrappers.lambdaQuery(SoInvoiceLine.class)
                .eq(SoInvoiceLine::getInvoiceId, entity.getInvoiceId()));
            for (SoInvoiceLine line : bo.getSoInvoiceLineList()) {
                line.setInvoiceId(entity.getInvoiceId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    /**
     * 批量删除销售发票（含明细行）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            lineMapper.delete(Wrappers.lambdaQuery(SoInvoiceLine.class)
                .eq(SoInvoiceLine::getInvoiceId, id));
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoInvoice> buildQueryWrapper(SoInvoiceBo bo) {
        LambdaQueryWrapper<SoInvoice> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getInvoiceNo()), SoInvoice::getInvoiceNo, bo.getInvoiceNo());
        lqw.eq(bo.getClientId() != null, SoInvoice::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoInvoice::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getInvoiceType()), SoInvoice::getInvoiceType, bo.getInvoiceType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoInvoice::getStatus, bo.getStatus());
        lqw.eq(bo.getSoId() != null, SoInvoice::getSoId, bo.getSoId());
        lqw.eq(bo.getContractId() != null, SoInvoice::getContractId, bo.getContractId());
        lqw.orderByDesc(SoInvoice::getCreateTime);
        return lqw;
    }
}
