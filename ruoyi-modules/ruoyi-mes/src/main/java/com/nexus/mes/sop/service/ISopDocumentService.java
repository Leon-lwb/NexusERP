package com.nexus.mes.sop.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.sop.domain.bo.SopDocumentBo;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopDocumentVo;

/**
 * MES SOP文档 Service 业务层接口
 * <p>
 * 状态机: DRAFT -> IN_REVIEW -> PUBLISHED -> ARCHIVED
 * 版本机: sop_version PENDING -> CURRENT -> HISTORICAL
 * 审批流: Flowable (WfCallBackServiceI) + 管理员兜底 approve/reject (R11)
 * 编码生成: autoCodeUtil.genSerialCode("MES_SOP_CODE", null) -> SOP+yyyyMMdd+3位日循环
 *
 * @author Nexus
 */
public interface ISopDocumentService {

    /** 状态: 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态: 审批中 */
    String STATUS_IN_REVIEW = "IN_REVIEW";
    /** 状态: 已发布 */
    String STATUS_PUBLISHED = "PUBLISHED";
    /** 状态: 已归档 */
    String STATUS_ARCHIVED = "ARCHIVED";

    /** 版本状态: 待发布 */
    String VERSION_PENDING = "PENDING";
    /** 版本状态: 当前 */
    String VERSION_CURRENT = "CURRENT";
    /** 版本状态: 历史 */
    String VERSION_HISTORICAL = "HISTORICAL";

    /**
     * 分页查询SOP文档列表
     *
     * @param bo         SOP查询条件
     * @param pageQuery  分页参数
     * @return 分页列表
     */
    TableDataInfo<SopDocumentVo> selectPageList(SopDocumentBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取SOP详情(含版本列表)
     *
     * @param sopId SOP文档ID
     * @return SOP详情
     */
    SopDocumentVo selectById(Long sopId);

    /**
     * 新增SOP(自动生成编码 + 建首版本 PENDING)
     *
     * @param bo SOP业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(SopDocumentBo bo);

    /**
     * 修改SOP(仅 DRAFT/ARCHIVED 可编辑;PUBLISHED 需先新增版本)
     *
     * @param bo SOP业务对象
     * @return 修改成功返回 true
     */
    Boolean updateByBo(SopDocumentBo bo);

    /**
     * 批量删除SOP(孤儿守卫:PUBLISHED 不可删;存在版本/绑定/查阅记录不可删)
     *
     * @param ids SOP文档ID集合
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 新增版本(DRAFT 或 PUBLISHED 上修订) -> sop_version PENDING, sop_document.status DRAFT
     *
     * @param bo 版本业务对象
     * @return 新增成功返回 true
     */
    Boolean addVersion(SopVersionBo bo);

    /**
     * 提交审批: DRAFT -> IN_REVIEW。selectFlowId 非空则走 Flowable, 否则直接 IN_REVIEW
     *
     * @param sopId         SOP文档ID
     * @param selectFlowId  Flowable流程定义ID(空则直接IN_REVIEW,管理员兜底审批)
     * @return 提交成功返回 true
     */
    Boolean submitForApproval(Long sopId, String selectFlowId);

    /**
     * 兜底审批通过(管理员强制, R11): IN_REVIEW -> PUBLISHED, 内部调 doPublish
     *
     * @param sopId  SOP文档ID
     * @param remark 审批意见
     * @return 审批通过返回 true
     */
    Boolean approve(Long sopId, String remark);

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     *
     * @param sopId  SOP文档ID
     * @param reason 驳回原因
     * @return 驳回成功返回 true
     */
    Boolean reject(Long sopId, String reason);

    /**
     * 归档(废止): PUBLISHED -> ARCHIVED
     *
     * @param sopId  SOP文档ID
     * @param reason 归档原因
     * @return 归档成功返回 true
     */
    Boolean archive(Long sopId, String reason);
}
