package com.nexus.core.so.service;

import java.util.List;
import com.nexus.core.so.domain.SoCustomerOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 客户订单Service接口
 *
 * @author nexus
 * @date 2025-10-21
 */
public interface ISoCustomerOrderService extends IService<SoCustomerOrder> {
    /**
     * 查询客户订单
     *
     * @param coId 客户订单主键
     * @return 客户订单
     */
    public SoCustomerOrder selectSoCustomerOrderByCoId(Long coId);

    /**
     * 查询客户订单列表
     *
     * @param soCustomerOrder 客户订单
     * @return 客户订单集合
     */
    public List<SoCustomerOrder> selectSoCustomerOrderList(SoCustomerOrder soCustomerOrder);

    /**
     * 新增客户订单
     *
     * @param soCustomerOrder 客户订单
     * @return 结果
     */
    public int insertSoCustomerOrder(SoCustomerOrder soCustomerOrder);

    /**
     * 修改客户订单
     *
     * @param soCustomerOrder 客户订单
     * @return 结果
     */
    public int updateSoCustomerOrder(SoCustomerOrder soCustomerOrder);

    /**
     * 批量删除客户订单
     *
     * @param coIds 需要删除的客户订单主键集合
     * @return 结果
     */
    public int deleteSoCustomerOrderByCoIds(Long[] coIds);

    /**
     * 删除客户订单信息
     *
     * @param coId 客户订单主键
     * @return 结果
     */
    public int deleteSoCustomerOrderByCoId(Long coId);
}
