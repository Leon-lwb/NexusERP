package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;

import java.util.List;

/**
 * 销售订单行Service接口
 *
 * @author nexus
 */
public interface ISoSalesOrderLineService {

    /**
     * 分页查询销售订单行列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售订单行分页列表
     */
    TableDataInfo<SoSalesOrderLineVo> selectPageList(SoSalesOrderLineBo bo, PageQuery pageQuery);

    /**
     * 查询销售订单行列表
     *
     * @param bo 查询参数
     * @return 销售订单行列表
     */
    List<SoSalesOrderLineVo> selectList(SoSalesOrderLineBo bo);

    /**
     * 根据ID查询销售订单行详情
     *
     * @param id 销售订单行ID
     * @return 销售订单行详情
     */
    SoSalesOrderLineVo selectById(Long id);

    /**
     * 新增销售订单行
     *
     * @param bo 销售订单行业务对象
     * @return 影响行数
     */
    int insertByBo(SoSalesOrderLineBo bo);

    /**
     * 修改销售订单行
     *
     * @param bo 销售订单行业务对象
     * @return 影响行数
     */
    int updateByBo(SoSalesOrderLineBo bo);

    /**
     * 批量删除销售订单行
     *
     * @param ids 销售订单行ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
