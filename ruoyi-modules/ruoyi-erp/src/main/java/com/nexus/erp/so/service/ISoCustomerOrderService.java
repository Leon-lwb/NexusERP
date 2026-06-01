package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.domain.vo.SoCustomerOrderVo;

import java.util.List;

/**
 * 客户订单Service接口
 *
 * @author nexus
 */
public interface ISoCustomerOrderService {

    /**
     * 分页查询客户订单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 客户订单分页列表
     */
    TableDataInfo<SoCustomerOrderVo> selectPageList(SoCustomerOrderBo bo, PageQuery pageQuery);

    /**
     * 查询客户订单列表
     *
     * @param bo 查询参数
     * @return 客户订单列表
     */
    List<SoCustomerOrderVo> selectList(SoCustomerOrderBo bo);

    /**
     * 根据ID查询客户订单详情
     *
     * @param id 客户订单ID
     * @return 客户订单详情
     */
    SoCustomerOrderVo selectById(Long id);

    /**
     * 新增客户订单
     *
     * @param bo 客户订单业务对象
     * @return 影响行数
     */
    int insertByBo(SoCustomerOrderBo bo);

    /**
     * 修改客户订单
     *
     * @param bo 客户订单业务对象
     * @return 影响行数
     */
    int updateByBo(SoCustomerOrderBo bo);

    /**
     * 批量删除客户订单
     *
     * @param ids 客户订单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
