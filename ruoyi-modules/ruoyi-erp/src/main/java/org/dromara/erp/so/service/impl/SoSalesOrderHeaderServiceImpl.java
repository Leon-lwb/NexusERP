package org.dromara.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.SoSalesOrderHeader;
import org.dromara.erp.so.domain.SoSalesOrderLine;
import org.dromara.erp.so.domain.bo.SoSalesOrderHeaderBo;
import org.dromara.erp.so.domain.bo.SoSalesOrderLineBo;
import org.dromara.erp.so.domain.vo.SoSalesOrderHeaderVo;
import org.dromara.erp.so.domain.vo.SoSalesOrderLineVo;
import org.dromara.erp.so.mapper.SoSalesOrderHeaderMapper;
import org.dromara.erp.so.mapper.SoSalesOrderLineMapper;
import org.dromara.erp.so.service.ISoSalesOrderHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 销售订单Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class SoSalesOrderHeaderServiceImpl implements ISoSalesOrderHeaderService {

    private final SoSalesOrderHeaderMapper baseMapper;
    private final SoSalesOrderLineMapper soSalesOrderLineMapper;

    @Override
    public TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = buildQueryWrapper(bo);
        Page<SoSalesOrderHeaderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SoSalesOrderHeaderVo selectById(Long id) {
        SoSalesOrderHeaderVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(
                new LambdaQueryWrapper<SoSalesOrderLine>()
                    .eq(SoSalesOrderLine::getSoId, id));
            vo.setSoSalesOrderLineList(MapstructUtils.convert(lineList, SoSalesOrderLineVo.class));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoSalesOrderHeaderBo bo) {
        SoSalesOrderHeader entity = MapstructUtils.convert(bo, SoSalesOrderHeader.class);
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .eq(SoSalesOrderLine::getSoId, entity.getSoId()));
        insertOrUpdateLines(entity.getSoId(), bo.getSoSalesOrderLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        soSalesOrderLineMapper.delete(new LambdaQueryWrapper<SoSalesOrderLine>()
            .in(SoSalesOrderLine::getSoId, List.of(ids)));
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo) {
        LambdaQueryWrapper<SoSalesOrderHeader> headerWrapper = Wrappers.lambdaQuery();
        headerWrapper.eq(SoSalesOrderHeader::getSoNo, soNo);
        SoSalesOrderHeader header = baseMapper.selectOne(headerWrapper);
        if (header == null) {
            return List.of();
        }
        LambdaQueryWrapper<SoSalesOrderLine> lineWrapper = Wrappers.lambdaQuery();
        lineWrapper.eq(SoSalesOrderLine::getSoId, header.getSoId());
        lineWrapper.orderByDesc(SoSalesOrderLine::getSortOrder);
        List<SoSalesOrderLine> lineList = soSalesOrderLineMapper.selectList(lineWrapper);
        return MapstructUtils.convert(lineList, SoSalesOrderLineVo.class);
    }

    /**
     * Batch insert or update sales order lines
     */
    private void insertOrUpdateLines(Long soId, List<SoSalesOrderLineBo> lineBoList) {
        if (lineBoList != null && !lineBoList.isEmpty()) {
            List<SoSalesOrderLine> lineList = MapstructUtils.convert(lineBoList, SoSalesOrderLine.class);
            for (SoSalesOrderLine line : lineList) {
                line.setSoId(soId);
            }
            soSalesOrderLineMapper.insertBatch(lineList);
        }
    }

    private LambdaQueryWrapper<SoSalesOrderHeader> buildQueryWrapper(SoSalesOrderHeaderBo bo) {
        LambdaQueryWrapper<SoSalesOrderHeader> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), SoSalesOrderHeader::getSoNo, bo.getSoNo());
        lqw.eq(bo.getCoId() != null, SoSalesOrderHeader::getCoId, bo.getCoId());
        lqw.eq(bo.getClientId() != null, SoSalesOrderHeader::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoSalesOrderHeader::getClientName, bo.getClientName());
        lqw.eq(bo.getSalesRepId() != null, SoSalesOrderHeader::getSalesRepId, bo.getSalesRepId());
        lqw.like(StringUtils.isNotBlank(bo.getSalesRepName()), SoSalesOrderHeader::getSalesRepName, bo.getSalesRepName());
        lqw.eq(bo.getOrderDate() != null, SoSalesOrderHeader::getOrderDate, bo.getOrderDate());
        lqw.eq(bo.getPromiseDate() != null, SoSalesOrderHeader::getPromiseDate, bo.getPromiseDate());
        lqw.eq(bo.getRequestedDate() != null, SoSalesOrderHeader::getRequestedDate, bo.getRequestedDate());
        lqw.eq(StringUtils.isNotBlank(bo.getCreditStatus()), SoSalesOrderHeader::getCreditStatus, bo.getCreditStatus());
        lqw.eq(bo.getCreditBlockedBy() != null, SoSalesOrderHeader::getCreditBlockedBy, bo.getCreditBlockedBy());
        lqw.eq(StringUtils.isNotBlank(bo.getOrderStatus()), SoSalesOrderHeader::getOrderStatus, bo.getOrderStatus());
        lqw.eq(bo.getProjectId() != null, SoSalesOrderHeader::getProjectId, bo.getProjectId());
        return lqw;
    }
}
