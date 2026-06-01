package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoSalesOrderLineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 销售订单行Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoSalesOrderLineServiceImpl implements ISoSalesOrderLineService {

    private final SoSalesOrderLineMapper baseMapper;

    /**
     * 分页查询销售订单行列表
     */
    @Override
    public TableDataInfo<SoSalesOrderLineVo> selectPageList(SoSalesOrderLineBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderLine> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderLineVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询销售订单行列表
     */
    @Override
    public List<SoSalesOrderLineVo> selectList(SoSalesOrderLineBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询销售订单行详情
     */
    @Override
    public SoSalesOrderLineVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderLineBo bo) {
        SoSalesOrderLine entity = MapstructUtils.convert(bo, SoSalesOrderLine.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderLineBo bo) {
        SoSalesOrderLine entity = MapstructUtils.convert(bo, SoSalesOrderLine.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除销售订单行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoSalesOrderLine> buildQueryWrapper(SoSalesOrderLineBo bo) {
        LambdaQueryWrapper<SoSalesOrderLine> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getSoId() != null, SoSalesOrderLine::getSoId, bo.getSoId());
        lqw.eq(StringUtils.isNotBlank(bo.getLineNo()), SoSalesOrderLine::getLineNo, bo.getLineNo());
        lqw.eq(bo.getMaterialId() != null, SoSalesOrderLine::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), SoSalesOrderLine::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), SoSalesOrderLine::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getLineStatus()), SoSalesOrderLine::getLineStatus, bo.getLineStatus());
        return lqw;
    }
}
