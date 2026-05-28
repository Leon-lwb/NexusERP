package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoSalesOrderLineBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;

import java.util.List;

/**
 * 销售订单行Service接口
 *
 * @author ruoyi
 */
public interface ISoSalesOrderLineService {

    TableDataInfo<SoSalesOrderLineVo> selectPageList(SoSalesOrderLineBo bo, PageQuery pageQuery);

    List<SoSalesOrderLineVo> selectList(SoSalesOrderLineBo bo);

    SoSalesOrderLineVo selectById(Long id);

    int insertByBo(SoSalesOrderLineBo bo);

    int updateByBo(SoSalesOrderLineBo bo);

    int deleteByIds(Long[] ids);
}
