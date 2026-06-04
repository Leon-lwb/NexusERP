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

    String STATUS_DRAFT = "DRAFT";
    String STATUS_APPROVING = "APPROVING";
    String STATUS_APPROVED = "APPROVED";
    String STATUS_EXECUTED = "EXECUTED";
    String STATUS_CANCELLED = "CANCELLED";

    TableDataInfo<WmTransferVo> selectPageList(WmTransferBo bo, PageQuery pageQuery);

    List<WmTransferVo> selectList(WmTransferBo bo);

    WmTransferVo selectById(Long id);

    int insertByBo(WmTransferBo bo);

    int updateByBo(WmTransferBo bo);

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
