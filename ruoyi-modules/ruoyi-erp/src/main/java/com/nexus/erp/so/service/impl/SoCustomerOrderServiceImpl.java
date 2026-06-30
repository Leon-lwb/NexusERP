package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.domain.vo.SoCustomerOrderVo;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.service.ISoCustomerOrderService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 客户订单Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoCustomerOrderServiceImpl implements ISoCustomerOrderService {

    private final SoCustomerOrderMapper baseMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询客户订单列表
     */
    @Override
    public TableDataInfo<SoCustomerOrderVo> selectPageList(SoCustomerOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoCustomerOrder> lqw = buildQueryWrapper(bo);
        Page<SoCustomerOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询客户订单列表
     */
    @Override
    public List<SoCustomerOrderVo> selectList(SoCustomerOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID查询客户订单详情
     */
    @Override
    public SoCustomerOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增客户订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoCustomerOrderBo bo) {
        SoCustomerOrder entity = MapstructUtils.convert(bo, SoCustomerOrder.class);
        if (StringUtils.isBlank(entity.getCoNo())) {
            entity.setCoNo(autoCodeUtil.genSerialCode("SO_CO_NO", null));
        }
        return baseMapper.insert(entity);
    }

    /**
     * 修改客户订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoCustomerOrderBo bo) {
        SoCustomerOrder entity = MapstructUtils.convert(bo, SoCustomerOrder.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除客户订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SoCustomerOrder> buildQueryWrapper(SoCustomerOrderBo bo) {
        LambdaQueryWrapper<SoCustomerOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getCoNo()), SoCustomerOrder::getCoNo, bo.getCoNo());
        lqw.eq(bo.getClientId() != null, SoCustomerOrder::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoCustomerOrder::getClientName, bo.getClientName());
        lqw.eq(bo.getOrderDate() != null, SoCustomerOrder::getOrderDate, bo.getOrderDate());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoCustomerOrder::getStatus, bo.getStatus());
        lqw.eq(bo.getPriority() != null, SoCustomerOrder::getPriority, bo.getPriority());
        lqw.eq(StringUtils.isNotBlank(bo.getCustomerPoNo()), SoCustomerOrder::getCustomerPoNo, bo.getCustomerPoNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceChannel()), SoCustomerOrder::getSourceChannel, bo.getSourceChannel());
        return lqw;
    }
}
