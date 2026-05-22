package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmProductionIssueBo;
import org.dromara.erp.wm.domain.vo.WmProductionIssueVo;

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
