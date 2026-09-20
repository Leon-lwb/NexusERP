package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 销售合同Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface ISoContractService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已生效 */
    String STATUS_EFFECTIVE = "EFFECTIVE";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";
    /** 已到期 */
    String STATUS_EXPIRED = "EXPIRED";
    /** 已终止 */
    String STATUS_TERMINATED = "TERMINATED";

    /**
     * 分页查询销售合同列表
     */
    TableDataInfo<SoContractVo> selectPageList(SoContractBo bo, PageQuery pageQuery);

    /**
     * 查询销售合同列表（不分页）
     */
    List<SoContractVo> selectList(SoContractBo bo);

    /**
     * 根据ID查询销售合同
     */
    SoContractVo selectById(Long id);

    /**
     * 新增销售合同
     */
    int insertByBo(SoContractBo bo);

    /**
     * 修改销售合同
     */
    int updateByBo(SoContractBo bo);

    /**
     * 批量删除销售合同
     */
    int deleteByIds(Long[] ids);

    /**
     * 提交流程审批（启动 Flowable 审批流）
     *
     * @param contractIdStr 合同ID字符串
     * @param selectFlowId  选择的审批流ID
     * @return 是否成功
     */
    boolean submit(String contractIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param contractIdStr 合同ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String contractIdStr);

    /**
     * 终止合同（仅已生效可终止）。
     *
     * <p>终止后合同不再履约，且不可再提交审批/修改。用于「双方协商解除」这类业务场景。</p>
     *
     * @param contractId 合同ID
     * @param reason     终止原因
     */
    void terminate(Long contractId, String reason);

    /**
     * 扫描并把已到期（{@code expiry_date < 今天}）的已生效合同置为 {@link #STATUS_EXPIRED}。
     *
     * <p>供定时任务调用，返回本次置为到期的合同数。</p>
     *
     * @return 本次更新的合同数
     */
    int expireOverdueContracts();
}
