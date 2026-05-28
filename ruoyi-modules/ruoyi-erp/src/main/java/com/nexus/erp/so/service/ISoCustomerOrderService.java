package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoCustomerOrderBo;
import com.nexus.erp.so.domain.vo.SoCustomerOrderVo;

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
