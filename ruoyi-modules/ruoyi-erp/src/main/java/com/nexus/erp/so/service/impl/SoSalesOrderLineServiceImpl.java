package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import java.util.Date;
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
     * 按条件查询销售订单行实体列表
     */
    @Override
    public List<SoSalesOrderLine> list(Wrapper<SoSalesOrderLine> wrapper) {
        return baseMapper.selectList(wrapper);
    }

    /**
     * 按销售订单ID和物料ID更新OQC状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOqcStatus(Long soId, Long materialId, String oqcStatus, Long oqcId, String oqcCode, Date oqcTime) {
        return baseMapper.update(null, Wrappers.<SoSalesOrderLine>lambdaUpdate()
            .set(SoSalesOrderLine::getOqcStatus, oqcStatus)
            .set(SoSalesOrderLine::getOqcId, oqcId)
            .set(SoSalesOrderLine::getOqcCode, oqcCode)
            .set(SoSalesOrderLine::getOqcTime, oqcTime)
            .eq(SoSalesOrderLine::getSoId, soId)
            .eq(SoSalesOrderLine::getMaterialId, materialId)
            .ne(SoSalesOrderLine::getLineStatus, "CANCELLED"));
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
