package com.nexus.erp.so.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoCustomerOrder;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.domain.vo.SoCustomerOrderVo;
import com.nexus.erp.so.mapper.SoCustomerOrderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.service.ISoCustomerOrderService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final SoSalesOrderHeaderMapper soSalesOrderHeaderMapper;
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
     * 批量删除客户订单。
     *
     * <p><b>引用守卫</b>：{@code so_sales_order_header.co_id} 指向客户订单（库内无外键约束），
     * 若客户订单已被销售订单引用，删除会让这些销售订单的「客户订单」关联悬空
     * （详情页回填不到 coNo，只能退化显示 {@code 客户订单#<id>}）。
     * 与合同/订单等单据「被引用即不可删」的口径一致，这里直接拒绝并提示。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        List<Long> idList = Arrays.stream(ids).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (idList.isEmpty()) {
            return 0;
        }
        for (Long id : idList) {
            Long refCount = soSalesOrderHeaderMapper.selectCount(
                Wrappers.<SoSalesOrderHeader>lambdaQuery().eq(SoSalesOrderHeader::getCoId, id));
            if (refCount != null && refCount > 0) {
                SoCustomerOrder existing = baseMapper.selectById(id);
                throw new ServiceException("客户订单【" + (existing == null ? id : existing.getCoNo())
                    + "】已被 " + refCount + " 张销售订单引用，不允许删除");
            }
        }
        return baseMapper.deleteBatchIds(idList);
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
        // 关键字模糊搜索：订单号 或 客户名称 任一匹配（移动端单输入框搜索）
        lqw.and(StringUtils.isNotBlank(bo.getKeyword()),
            w -> w.like(SoCustomerOrder::getCoNo, bo.getKeyword())
                .or()
                .like(SoCustomerOrder::getClientName, bo.getKeyword()));
        return lqw;
    }
}
