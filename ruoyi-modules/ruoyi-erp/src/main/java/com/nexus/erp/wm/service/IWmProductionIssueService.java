package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;

import java.util.List;

public interface IWmProductionIssueService {

    TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery);

    List<WmProductionIssueVo> selectList(WmProductionIssueBo bo);

    WmProductionIssueVo selectById(Long id);

    int insertByBo(WmProductionIssueBo bo);

    int updateByBo(WmProductionIssueBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行生产领料过账（扣减库存）
     */
    void execute(Long issueId);
}
