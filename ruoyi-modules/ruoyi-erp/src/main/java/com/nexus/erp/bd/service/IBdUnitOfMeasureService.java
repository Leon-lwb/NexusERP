package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdUnitOfMeasureBo;
import com.nexus.erp.bd.domain.vo.BdUnitOfMeasureVo;

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
    List<BdUnitOfMeasureVo> selectByPrimaryId(Long primaryId);
    List<String> selectCategories();
    int insertByBo(BdUnitOfMeasureBo bo);
    int updateByBo(BdUnitOfMeasureBo bo);
    int deleteByIds(Long[] ids);
}
