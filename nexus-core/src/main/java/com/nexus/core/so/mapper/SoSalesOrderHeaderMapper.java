package com.nexus.core.so.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.so.domain.SoSalesOrderHeader;
import com.nexus.core.so.domain.SoSalesOrderLine;

/**
 * 销售订单Mapper接口
 *
 * @author nexus
 * @date 2025-10-22
 */
public interface SoSalesOrderHeaderMapper extends BaseMapper<SoSalesOrderHeader> {
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
     * 删除销售订单
     *
     * @param soId 销售订单主键
     * @return 结果
     */
    public int deleteSoSalesOrderHeaderBySoId(Long soId);

    /**
     * 批量删除销售订单
     *
     * @param soIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSoSalesOrderHeaderBySoIds(Long[] soIds);

        /**
         * 批量删除销售订单行
         *
         * @param soIds 需要删除的数据主键集合
         * @return 结果
         */
        public int deleteSoSalesOrderLineBySoIds(Long[] soIds);

        /**
         * 批量新增销售订单行
         *
         * @param soSalesOrderLineList 销售订单行列表
         * @return 结果
         */
        public int batchSoSalesOrderLine(List<SoSalesOrderLine> soSalesOrderLineList);


        /**
         * 通过销售订单主键删除销售订单行信息
         *
         * @param soId 销售订单ID
         * @return 结果
         */
        public int deleteSoSalesOrderLineBySoId(Long soId);
}
