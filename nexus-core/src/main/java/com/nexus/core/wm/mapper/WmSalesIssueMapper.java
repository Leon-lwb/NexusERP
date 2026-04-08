package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmSalesIssue;
import com.nexus.core.wm.domain.WmSalesIssueLine;

/**
 * 销售出库Mapper接口
 *
 * @author nexus
 * @date 2026-04-03
 */
public interface WmSalesIssueMapper extends BaseMapper<WmSalesIssue> {
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
     * 删除销售出库
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    public int deleteWmSalesIssueByIssueId(Long issueId);

    /**
     * 批量删除销售出库
     *
     * @param issueIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmSalesIssueByIssueIds(Long[] issueIds);

        /**
         * 批量删除销售出库明细
         *
         * @param issueIds 需要删除的数据主键集合
         * @return 结果
         */
        public int deleteWmSalesIssueLineByIssueIds(Long[] issueIds);

        /**
         * 批量新增销售出库明细
         *
         * @param wmSalesIssueLineList 销售出库明细列表
         * @return 结果
         */
        public int batchWmSalesIssueLine(List<WmSalesIssueLine> wmSalesIssueLineList);


        /**
         * 通过销售出库主键删除销售出库明细信息
         *
         * @param issueId 销售出库ID
         * @return 结果
         */
        public int deleteWmSalesIssueLineByIssueId(Long issueId);
}
