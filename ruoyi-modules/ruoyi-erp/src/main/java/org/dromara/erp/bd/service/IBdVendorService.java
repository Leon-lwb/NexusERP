package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdVendorBo;
import org.dromara.erp.bd.domain.vo.BdVendorVo;

import java.util.List;

public interface IBdVendorService {
    TableDataInfo<BdVendorVo> selectPageList(BdVendorBo bo, PageQuery pageQuery);
    List<BdVendorVo> selectList(BdVendorBo bo);
    BdVendorVo selectById(Long id);
    int insertByBo(BdVendorBo bo);
    int updateByBo(BdVendorBo bo);
    int deleteByIds(Long[] ids);
}
