package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdMaterialBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;

import java.util.List;

public interface IBdMaterialService {
    TableDataInfo<BdMaterialVo> selectPageList(BdMaterialBo bo, PageQuery pageQuery);
    List<BdMaterialVo> selectList(BdMaterialBo bo);
    BdMaterialVo selectById(Long id);
    int insertByBo(BdMaterialBo bo);
    int updateByBo(BdMaterialBo bo);
    int deleteByIds(Long[] ids);
    List<BdMaterialVo> selectByCategoryIds(List<Long> categoryIds);
}
