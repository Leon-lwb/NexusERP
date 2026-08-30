package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmTransferBo;
import com.nexus.erp.wm.domain.vo.WmTransferVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 库存调拨单Service接口
 *
 * @author nexus
 */
public interface IWmTransferService extends WfCallBackServiceI {

    /** 常量：STATUSDRAFT */
    String STATUS_DRAFT = "DRAFT";
    /** 常量：STATUSAPPROVING */
    String STATUS_APPROVING = "APPROVING";
    /** 常量：STATUSAPPROVED */
    String STATUS_APPROVED = "APPROVED";
    /** 常量：两段式移库中间态(全部行已下架, 待上架确认) */
    String STATUS_TAKEN_OFF = "TAKEN_OFF";
    /** 常量：STATUSEXECUTED */
    String STATUS_EXECUTED = "EXECUTED";
    /** 常量：STATUSCANCELLED */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询调拨列表
     *
     * @param bo 调拨业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmTransferVo> selectPageList(WmTransferBo bo, PageQuery pageQuery);

    /**
     * 查询调拨列表
     *
     * @param bo 调拨业务对象（BO）
     * @return 结果列表
     */
    List<WmTransferVo> selectList(WmTransferBo bo);

    /**
     * 查询调拨
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmTransferVo selectById(Long id);

    /**
     * 新增调拨
     *
     * @param bo 调拨业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmTransferBo bo);

    /**
     * 修改调拨
     *
     * @param bo 调拨业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmTransferBo bo);

    /**
     * 删除调拨
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行调拨（库存过账：来源仓库扣减 → 目标仓库增加）
     */
    void execute(Long transferId);

    // ==================== Flowable 工作流 ====================

    /**
     * 发起审批流程
     */
    boolean submit(String transferIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String transferIdStr);
}
