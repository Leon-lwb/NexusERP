package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.domain.vo.WmStockTakingPlanVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

public interface IWmStockTakingPlanService extends WfCallBackServiceI {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_GENERATED = "GENERATED";
    String STATUS_APPROVING = "APPROVING";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_REJECTED = "REJECTED";
    String STATUS_POSTED = "POSTED";

    TableDataInfo<WmStockTakingPlanVo> selectPageList(WmStockTakingPlanBo bo, PageQuery pageQuery);

    List<WmStockTakingPlanVo> selectList(WmStockTakingPlanBo bo);

    WmStockTakingPlanVo selectById(Long id);

    int insertByBo(WmStockTakingPlanBo bo);

    int updateByBo(WmStockTakingPlanBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 生成盘点行（从库存快照生成盘点明细）
     */
    void generateLines(Long planId);

    /**
     * 执行盘点过账（将差异更新到库存）
     */
    void postAdjustment(Long planId);

    // ==================== Flowable 工作流 ====================

    /**
     * 提交盘点计划审批
     *
     * @param planIdStr    盘点计划ID字符串
     * @param selectFlowId 审批流程ID
     * @return 是否成功
     */
    boolean submit(String planIdStr, String selectFlowId);

    /**
     * 获取盘点计划流程实例ID
     *
     * @param planIdStr 盘点计划ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String planIdStr);
}
