package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmSalesIssue;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 销售出库Service接口
 *
 * @author nexus
 * @date 2026-04-03
 */
public interface IWmSalesIssueService extends IService<WmSalesIssue> {
    /**
     * 查询销售出库
     *
     * @param issueId 销售出库主键
     * @return 销售出库
     */
    public WmSalesIssue selectWmSalesIssueByIssueId(Long issueId);

    /**
     * 查询销售出库列表
     *
     * @param wmSalesIssue 销售出库
     * @return 销售出库集合
     */
    public List<WmSalesIssue> selectWmSalesIssueList(WmSalesIssue wmSalesIssue);

    /**
     * 新增销售出库
     *
     * @param wmSalesIssue 销售出库
     * @return 结果
     */
    public int insertWmSalesIssue(WmSalesIssue wmSalesIssue);

    /**
     * 修改销售出库
     *
     * @param wmSalesIssue 销售出库
     * @return 结果
     */
    public int updateWmSalesIssue(WmSalesIssue wmSalesIssue);

    /**
     * 批量删除销售出库
     *
     * @param issueIds 需要删除的销售出库主键集合
     * @return 结果
     */
    public int deleteWmSalesIssueByIssueIds(Long[] issueIds);

    /**
     * 删除销售出库信息
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    public int deleteWmSalesIssueByIssueId(Long issueId);

    /**
     * 确认销售出库
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    public int confirmSalesIssue(Long issueId);

    /**
     * 完成销售出库（扣减库存）
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    public int completeSalesIssue(Long issueId);
}
