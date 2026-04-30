package org.dromara.erp.so.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.bo.SoCustomerOrderBo;
import org.dromara.erp.so.domain.vo.SoCustomerOrderVo;

import java.util.List;

/**
 * 客户订单Service接口
 *
 * @author ruoyi
 */
public interface ISoCustomerOrderService {

    TableDataInfo<SoCustomerOrderVo> selectPageList(SoCustomerOrderBo bo, PageQuery pageQuery);

    List<SoCustomerOrderVo> selectList(SoCustomerOrderBo bo);

    SoCustomerOrderVo selectById(Long id);

    int insertByBo(SoCustomerOrderBo bo);

    int updateByBo(SoCustomerOrderBo bo);

    int deleteByIds(Long[] ids);
}
