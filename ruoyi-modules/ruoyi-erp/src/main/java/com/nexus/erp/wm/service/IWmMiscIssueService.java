package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMiscIssueBo;
import com.nexus.erp.wm.domain.vo.WmMiscIssueVo;

import java.util.List;

public interface IWmMiscIssueService {

    TableDataInfo<WmMiscIssueVo> selectPageList(WmMiscIssueBo bo, PageQuery pageQuery);

    List<WmMiscIssueVo> selectList(WmMiscIssueBo bo);

    WmMiscIssueVo selectById(Long id);

    int insertByBo(WmMiscIssueBo bo);

    int updateByBo(WmMiscIssueBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行杂项出库过账（扣减库存）
     */
    void execute(Long issueId);
}
