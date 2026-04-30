package org.dromara.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.SoCustomerOrder;
import org.dromara.erp.so.domain.bo.SoCustomerOrderBo;
import org.dromara.erp.so.domain.vo.SoCustomerOrderVo;
import org.dromara.erp.so.mapper.SoCustomerOrderMapper;
import org.dromara.erp.so.service.ISoCustomerOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 客户订单Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class SoCustomerOrderServiceImpl implements ISoCustomerOrderService {

    private final SoCustomerOrderMapper baseMapper;

    @Override
    public TableDataInfo<SoCustomerOrderVo> selectPageList(SoCustomerOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoCustomerOrder> lqw = buildQueryWrapper(bo);
        Page<SoCustomerOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SoCustomerOrderVo> selectList(SoCustomerOrderBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SoCustomerOrderVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoCustomerOrderBo bo) {
        SoCustomerOrder entity = MapstructUtils.convert(bo, SoCustomerOrder.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoCustomerOrderBo bo) {
        SoCustomerOrder entity = MapstructUtils.convert(bo, SoCustomerOrder.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

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
