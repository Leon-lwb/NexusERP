package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdWorkshopBo;
import com.nexus.erp.bd.domain.vo.BdWorkshopVo;

import java.util.List;

/**
 * 车间信息Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkshopService {
    TableDataInfo<BdWorkshopVo> selectPageList(BdWorkshopBo bo, PageQuery pageQuery);
    List<BdWorkshopVo> selectList(BdWorkshopBo bo);
    BdWorkshopVo selectById(Long id);
    int insertByBo(BdWorkshopBo bo);
    int updateByBo(BdWorkshopBo bo);
    int deleteByIds(Long[] ids);
}
