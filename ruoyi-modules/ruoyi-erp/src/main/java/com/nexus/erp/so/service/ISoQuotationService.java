package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationVo;

import java.util.List;

/**
 * 销售报价Service接口
 *
 * @author ruoyi
 */
public interface ISoQuotationService {

    /** 草稿状态 */
    String STATUS_DRAFT = "DRAFT";
    /** 已确认状态 */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 已转换状态 */
    String STATUS_CONVERTED = "CONVERTED";
    /** 已关闭状态 */
    String STATUS_CLOSED = "CLOSED";

    /**
     * 分页查询销售报价列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售报价分页列表
     */
    TableDataInfo<SoQuotationVo> selectPageList(SoQuotationBo bo, PageQuery pageQuery);

    /**
     * 查询销售报价列表
     *
     * @param bo 查询参数
     * @return 销售报价列表
     */
    List<SoQuotationVo> selectList(SoQuotationBo bo);

    /**
     * 根据ID查询销售报价详情
     *
     * @param id 销售报价ID
     * @return 销售报价详情
     */
    SoQuotationVo selectById(Long id);

    /**
     * 新增销售报价
     *
     * @param bo 销售报价业务对象
     * @return 影响行数
     */
    int insertByBo(SoQuotationBo bo);

    /**
     * 修改销售报价
     *
     * @param bo 销售报价业务对象
     * @return 影响行数
     */
    int updateByBo(SoQuotationBo bo);

    /**
     * 批量删除销售报价
     *
     * @param ids 销售报价ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认销售报价
     *
     * @param quotationId 销售报价ID
     */
    void confirm(Long quotationId);

    /**
     * 将销售报价转换为销售订单
     *
     * @param quotationId 销售报价ID
     * @return 生成的销售订单ID
     */
    Long convertToSalesOrder(Long quotationId);
}
