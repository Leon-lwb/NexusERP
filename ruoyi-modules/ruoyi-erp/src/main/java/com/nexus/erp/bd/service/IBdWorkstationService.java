package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdWorkstationBo;
import com.nexus.erp.bd.domain.vo.BdWorkstationVo;

import java.util.List;

/**
 * 工作中心Service接口
 *
 * @author wanghuan
 */
public interface IBdWorkstationService {
    TableDataInfo<BdWorkstationVo> selectPageList(BdWorkstationBo bo, PageQuery pageQuery);
    List<BdWorkstationVo> selectList(BdWorkstationBo bo);
    BdWorkstationVo selectById(Long id);
    int insertByBo(BdWorkstationBo bo);
    int updateByBo(BdWorkstationBo bo);
    int deleteByIds(Long[] ids);
}
