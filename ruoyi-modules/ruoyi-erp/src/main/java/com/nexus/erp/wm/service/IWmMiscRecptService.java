package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMiscRecptBo;
import com.nexus.erp.wm.domain.vo.WmMiscRecptVo;

import java.util.List;

public interface IWmMiscRecptService {

    TableDataInfo<WmMiscRecptVo> selectPageList(WmMiscRecptBo bo, PageQuery pageQuery);

    List<WmMiscRecptVo> selectList(WmMiscRecptBo bo);

    WmMiscRecptVo selectById(Long id);

    int insertByBo(WmMiscRecptBo bo);

    int updateByBo(WmMiscRecptBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行杂项入库过账（增加库存）
     */
    void execute(Long recptId);
}
