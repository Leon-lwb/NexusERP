package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmSalesIssueBo;
import com.nexus.erp.wm.domain.vo.WmSalesIssueVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 销售出库Service接口
 *
 * @author nexus
 * @date 2026-04-03
 */
public interface IWmSalesIssueService extends WfCallBackServiceI {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_APPROVING = "APPROVING";
    String STATUS_APPROVED = "APPROVED";
    String STATUS_REJECTED = "REJECTED";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询销售出库列表
     *
     * @param bo         销售出库业务对象
     * @param pageQuery  分页参数
     * @return 销售出库分页集合
     */
    TableDataInfo<WmSalesIssueVo> selectPageList(WmSalesIssueBo bo, PageQuery pageQuery);

    /**
     * 查询销售出库列表
     *
     * @param bo 销售出库业务对象
     * @return 销售出库集合
     */
    List<WmSalesIssueVo> selectList(WmSalesIssueBo bo);

    /**
     * 查询销售出库
     *
     * @param id 销售出库主键
     * @return 销售出库
     */
    WmSalesIssueVo selectById(Long id);

    /**
     * 新增销售出库
     *
     * @param bo 销售出库业务对象
     * @return 结果
     */
    int insertByBo(WmSalesIssueBo bo);

    /**
     * 修改销售出库
     *
     * @param bo 销售出库业务对象
     * @return 结果
     */
    int updateByBo(WmSalesIssueBo bo);

    /**
     * 批量删除销售出库
     *
     * @param ids 需要删除的销售出库主键集合
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认销售出库
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    int confirmSalesIssue(Long issueId);

    /**
     * 完成销售出库（扣减库存）
     *
     * @param issueId 销售出库主键
     * @return 结果
     */
    int completeSalesIssue(Long issueId);

    // ==================== Flowable 工作流 ====================

    /**
     * 发起审批流程
     */
    boolean submit(String issueIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String issueIdStr);
}
