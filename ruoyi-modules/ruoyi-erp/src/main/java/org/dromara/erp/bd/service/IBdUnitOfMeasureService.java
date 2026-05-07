package org.dromara.erp.bd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.bd.domain.bo.BdUnitOfMeasureBo;
import org.dromara.erp.bd.domain.vo.BdUnitOfMeasureVo;

import java.util.List;

/**
 * 计量单位Service接口
 *
 * @author wanghuan
 */
public interface IBdUnitOfMeasureService {
    TableDataInfo<BdUnitOfMeasureVo> selectPageList(BdUnitOfMeasureBo bo, PageQuery pageQuery);
    List<BdUnitOfMeasureVo> selectList(BdUnitOfMeasureBo bo);
    BdUnitOfMeasureVo selectById(Long id);
    int insertByBo(BdUnitOfMeasureBo bo);
    int updateByBo(BdUnitOfMeasureBo bo);
    int deleteByIds(Long[] ids);
}
