package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmProductionRecptVo;

import java.util.List;

public interface IWmProductionRecptService {

    TableDataInfo<WmProductionRecptVo> selectPageList(WmProductionRecptBo bo, PageQuery pageQuery);

    List<WmProductionRecptVo> selectList(WmProductionRecptBo bo);

    WmProductionRecptVo selectById(Long id);

    int insertByBo(WmProductionRecptBo bo);

    int updateByBo(WmProductionRecptBo bo);

    int deleteByIds(Long[] ids);

    void execute(Long recptId);
}
