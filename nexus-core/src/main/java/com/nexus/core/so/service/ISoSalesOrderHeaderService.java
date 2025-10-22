package com.nexus.core.so.service;

import java.util.List;
import com.nexus.core.so.domain.SoSalesOrderHeader;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 销售订单Service接口
 *
 * @author nexus
 * @date 2025-10-22
 */
public interface ISoSalesOrderHeaderService extends IService<SoSalesOrderHeader> {
    /**
     * 查询销售订单
     *
     * @param soId 销售订单主键
     * @return 销售订单
     */
    public SoSalesOrderHeader selectSoSalesOrderHeaderBySoId(Long soId);

    /**
     * 查询销售订单列表
     *
     * @param soSalesOrderHeader 销售订单
     * @return 销售订单集合
     */
    public List<SoSalesOrderHeader> selectSoSalesOrderHeaderList(SoSalesOrderHeader soSalesOrderHeader);

    /**
     * 新增销售订单
     *
     * @param soSalesOrderHeader 销售订单
     * @return 结果
     */
    public int insertSoSalesOrderHeader(SoSalesOrderHeader soSalesOrderHeader);

    /**
     * 修改销售订单
     *
     * @param soSalesOrderHeader 销售订单
     * @return 结果
     */
    public int updateSoSalesOrderHeader(SoSalesOrderHeader soSalesOrderHeader);

    /**
     * 批量删除销售订单
     *
     * @param soIds 需要删除的销售订单主键集合
     * @return 结果
     */
    public int deleteSoSalesOrderHeaderBySoIds(Long[] soIds);

    /**
     * 删除销售订单信息
     *
     * @param soId 销售订单主键
     * @return 结果
     */
    public int deleteSoSalesOrderHeaderBySoId(Long soId);
}
