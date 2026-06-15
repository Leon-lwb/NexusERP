package com.nexus.erp.pu.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.bo.PuContractBo;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 采购合同Service接口
 *
 * @author nexus
 * @date 2026-06-15
 */
public interface IPuContractService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已批准 */
    String STATUS_APPROVED = "APPROVED";
    /** 执行中 */
    String STATUS_EXECUTING = "EXECUTING";
    /** 已完成 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 已终止 */
    String STATUS_TERMINATED = "TERMINATED";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";

    /**
     * 分页查询采购合同列表
     */
    TableDataInfo<PuContractVo> selectPageList(PuContractBo bo, PageQuery pageQuery);

    /**
     * 查询采购合同列表（不分页）
     */
    List<PuContractVo> selectList(PuContractBo bo);

    /**
     * 根据ID查询采购合同
     */
    PuContractVo selectById(Long id);

    /**
     * 新增采购合同
     */
    int insertByBo(PuContractBo bo);

    /**
     * 修改采购合同
     */
    int updateByBo(PuContractBo bo);

    /**
     * 批量删除采购合同
     */
    int deleteByIds(Long[] ids);

    /**
     * 提交流程审批
     */
    boolean submit(String contractIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String contractIdStr);
}
