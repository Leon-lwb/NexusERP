package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;

import java.util.List;

/**
 * 销售发票Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface ISoInvoiceService {

    /**
     * 分页查询销售发票列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售发票分页列表
     */
    TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery);

    /**
     * 查询销售发票列表（不分页）
     *
     * @param bo 查询参数
     * @return 销售发票列表
     */
    List<SoInvoiceVo> selectList(SoInvoiceBo bo);

    /**
     * 根据ID查询销售发票
     *
     * @param id 发票ID
     * @return 销售发票详细信息
     */
    SoInvoiceVo selectById(Long id);

    /**
     * 新增销售发票
     *
     * @param bo 销售发票业务对象
     * @return 影响行数
     */
    int insertByBo(SoInvoiceBo bo);

    /**
     * 修改销售发票
     *
     * @param bo 销售发票业务对象
     * @return 影响行数
     */
    int updateByBo(SoInvoiceBo bo);

    /**
     * 批量删除销售发票
     *
     * @param ids 发票ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);
}
