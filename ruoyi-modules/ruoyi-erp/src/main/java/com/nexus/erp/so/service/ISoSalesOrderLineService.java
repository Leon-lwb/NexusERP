package com.nexus.erp.so.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;

import java.util.Date;
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

    /**
     * 按条件查询销售订单行实体列表
     *
     * @param wrapper 查询条件
     * @return 销售订单行实体列表
     */
    List<SoSalesOrderLine> list(Wrapper<SoSalesOrderLine> wrapper);

    /**
     * 按销售订单ID和物料ID更新OQC状态
     *
     * @param soId       销售订单ID
     * @param materialId 物料ID
     * @param oqcStatus  OQC检验状态
     * @param oqcId      OQC检验单ID
     * @param oqcCode    OQC检验单号
     * @param oqcTime    检验时间
     * @return 影响行数
     */
    int updateOqcStatus(Long soId, Long materialId, String oqcStatus, Long oqcId, String oqcCode, Date oqcTime);
}
