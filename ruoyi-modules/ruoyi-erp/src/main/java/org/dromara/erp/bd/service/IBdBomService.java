package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdBomBo;
import org.dromara.erp.bd.domain.vo.BdBomVo;

import java.util.List;

public interface IBdBomService {
    TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery);
    List<BdBomVo> selectList(BdBomBo bo);
    BdBomVo selectById(Long id);
    int insertByBo(BdBomBo bo);
    int updateByBo(BdBomBo bo);
    int deleteByIds(Long[] ids);
}
