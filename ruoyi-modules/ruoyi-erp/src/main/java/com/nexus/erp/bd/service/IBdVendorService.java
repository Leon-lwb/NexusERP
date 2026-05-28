package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdVendorBo;
import com.nexus.erp.bd.domain.vo.BdVendorVo;

import java.util.List;

/**
 * 供应商信息Service接口
 *
 * @author wanghuan
 */
public interface IBdVendorService {
    TableDataInfo<BdVendorVo> selectPageList(BdVendorBo bo, PageQuery pageQuery);
    List<BdVendorVo> selectList(BdVendorBo bo);
    BdVendorVo selectById(Long id);
    int insertByBo(BdVendorBo bo);
    int updateByBo(BdVendorBo bo);
    int deleteByIds(Long[] ids);
}
