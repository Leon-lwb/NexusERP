package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmStockTakingPlanBo;
import com.nexus.erp.wm.domain.vo.WmStockTakingPlanVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;
/**
 * 库存盘点计划服务接口
 * <p>
 * 定义库存盘点计划的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IWmStockTakingPlanService extends WfCallBackServiceI {

    /** 常量：STATUSDRAFT */
    String STATUS_DRAFT = "DRAFT";
    /** 常量：STATUSGENERATED */
    String STATUS_GENERATED = "GENERATED";
    /** 常量：STATUSAPPROVING */
    String STATUS_APPROVING = "APPROVING";
    /** 常量：STATUSCOMPLETED */
    String STATUS_COMPLETED = "COMPLETED";
    /** 常量：STATUSREJECTED */
    String STATUS_REJECTED = "REJECTED";
    /** 常量：STATUSPOSTED */
    String STATUS_POSTED = "POSTED";

    /**
     * 分页查询库存盘点计划列表
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmStockTakingPlanVo> selectPageList(WmStockTakingPlanBo bo, PageQuery pageQuery);

    /**
     * 查询库存盘点计划列表
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 结果列表
     */
    List<WmStockTakingPlanVo> selectList(WmStockTakingPlanBo bo);

    /**
     * 查询库存盘点计划
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmStockTakingPlanVo selectById(Long id);

    /**
     * 新增库存盘点计划
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmStockTakingPlanBo bo);

    /**
     * 修改库存盘点计划
     *
     * @param bo 库存盘点计划业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmStockTakingPlanBo bo);

    /**
     * 删除库存盘点计划
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
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
