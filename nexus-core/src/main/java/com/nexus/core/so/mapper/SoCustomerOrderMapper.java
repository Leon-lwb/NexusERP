package com.nexus.core.so.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.so.domain.SoCustomerOrder;

/**
 * 客户订单Mapper接口
 *
 * @author nexus
 * @date 2025-10-21
 */
public interface SoCustomerOrderMapper extends BaseMapper<SoCustomerOrder> {
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
     * 删除客户订单
     *
     * @param coId 客户订单主键
     * @return 结果
     */
    public int deleteSoCustomerOrderByCoId(Long coId);

    /**
     * 批量删除客户订单
     *
     * @param coIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSoCustomerOrderByCoIds(Long[] coIds);
}
