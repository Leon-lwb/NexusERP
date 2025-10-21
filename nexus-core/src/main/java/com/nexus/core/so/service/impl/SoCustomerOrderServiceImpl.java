package com.nexus.core.so.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.so.mapper.SoCustomerOrderMapper;
import com.nexus.core.so.domain.SoCustomerOrder;
import com.nexus.core.so.service.ISoCustomerOrderService;

/**
 * 客户订单Service业务层处理
 *
 * @author nexus
 * @date 2025-10-21
 */
@Service
public class SoCustomerOrderServiceImpl extends ServiceImpl<SoCustomerOrderMapper, SoCustomerOrder> implements ISoCustomerOrderService {

    @Autowired
    private SoCustomerOrderMapper soCustomerOrderMapper;

    /**
     * 查询客户订单
     *
     * @param coId 客户订单主键
     * @return 客户订单
     */
    @Override
    public SoCustomerOrder selectSoCustomerOrderByCoId(Long coId) {
        return soCustomerOrderMapper.selectSoCustomerOrderByCoId(coId);
    }

    /**
     * 查询客户订单列表
     *
     * @param soCustomerOrder 客户订单
     * @return 客户订单
     */
    @Override
    public List<SoCustomerOrder> selectSoCustomerOrderList(SoCustomerOrder soCustomerOrder) {
        return soCustomerOrderMapper.selectSoCustomerOrderList(soCustomerOrder);
    }

    /**
     * 新增客户订单
     *
     * @param soCustomerOrder 客户订单
     * @return 结果
     */
    @Override
    public int insertSoCustomerOrder(SoCustomerOrder soCustomerOrder) {
                soCustomerOrder.setCreateTime(DateUtils.getNowDate());
            return soCustomerOrderMapper.insertSoCustomerOrder(soCustomerOrder);
    }

    /**
     * 修改客户订单
     *
     * @param soCustomerOrder 客户订单
     * @return 结果
     */
    @Override
    public int updateSoCustomerOrder(SoCustomerOrder soCustomerOrder) {
                soCustomerOrder.setUpdateTime(DateUtils.getNowDate());
        return soCustomerOrderMapper.updateSoCustomerOrder(soCustomerOrder);
    }

    /**
     * 批量删除客户订单
     *
     * @param coIds 需要删除的客户订单主键
     * @return 结果
     */
    @Override
    public int deleteSoCustomerOrderByCoIds(Long[] coIds) {
        return soCustomerOrderMapper.deleteSoCustomerOrderByCoIds(coIds);
    }

    /**
     * 删除客户订单信息
     *
     * @param coId 客户订单主键
     * @return 结果
     */
    @Override
    public int deleteSoCustomerOrderByCoId(Long coId) {
        return soCustomerOrderMapper.deleteSoCustomerOrderByCoId(coId);
    }
}
