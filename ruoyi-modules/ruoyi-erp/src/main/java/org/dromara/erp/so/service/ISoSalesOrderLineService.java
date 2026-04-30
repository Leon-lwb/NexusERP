package org.dromara.erp.so.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.bo.SoSalesOrderLineBo;
import org.dromara.erp.so.domain.vo.SoSalesOrderLineVo;

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
