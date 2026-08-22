package com.nexus.mes.release.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.release.domain.bo.ReleasePackageBo;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.workflow.domain.WfCustomForm;

import java.util.List;

/**
 * MES客户定制化发布包 Service 接口
 * <p>状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED
 * 审批流: 实现 WfCallBackServiceI (Flowable afterFlowHandle 驱动状态机) + 管理员兜底 approve/reject
 * 匹配: clientId + materialId + customerProductCode 精确匹配 -> 客户+物料默认包</p>
 *
 * @author Nexus
 */
public interface IReleasePackageService {

    /** 服务Bean名(Flowable wf_custom_form.business_service 注册名) */
    String SERVICE_NAME = "mesReleasePackageService";

    /** 状态: 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态: 审批中 */
    String STATUS_IN_REVIEW = "IN_REVIEW";
    /** 状态: 已发布 */
    String STATUS_RELEASED = "RELEASED";
    /** 状态: 已归档 */
    String STATUS_ARCHIVED = "ARCHIVED";

    /** 是否默认: 是 */
    String IS_DEFAULT_YES = "1";
    /** 是否默认: 否 */
    String IS_DEFAULT_NO = "0";

    // ==================== CRUD ====================

    /**
     * 分页查询发布包列表
     */
    TableDataInfo<ReleasePackageVo> selectPageList(ReleasePackageBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取发布包(不含子表)
     */
    ReleasePackageVo selectById(Long packageId);

    /**
     * 根据ID获取发布包详情(含固件映射/BOM选配/配置项三个子表)
     */
    ReleasePackageVo selectDetailById(Long packageId);

    /**
     * 新增发布包(自动生成编码 + 状态 DRAFT + 子表级联保存)
     */
    Boolean insertByBo(ReleasePackageBo bo);

    /**
     * 修改发布包(仅 DRAFT/ARCHIVED 可编辑; 子表先删后插)
     */
    Boolean updateByBo(ReleasePackageBo bo);

    /**
     * 批量删除发布包(仅 DRAFT 可删)
     */
    Boolean deleteByIds(Long[] ids);

    // ==================== 状态机 ====================

    /**
     * 提交审批: DRAFT -> IN_REVIEW
     *
     * @param packageId   发布包ID
     * @param selectFlowId 审批流程配置ID(wf_custom_form.id, 可空, 空则仅改状态)
     */
    Boolean submitForApproval(Long packageId, String selectFlowId);

    /**
     * 兜底审批通过(管理员强制): IN_REVIEW -> RELEASED
     */
    Boolean approve(Long packageId, String remark);

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    Boolean reject(Long packageId, String reason);

    /**
     * 归档: RELEASED -> ARCHIVED (归档后不参与匹配; 已生成工单的快照不受影响)
     */
    Boolean archive(Long packageId, String reason);

    /**
     * 设为客户+物料默认发布包(仅 RELEASED, 客户+物料内唯一)
     */
    Boolean setDefault(Long packageId);

    // ==================== 查询匹配 ====================

    /**
     * 按客户+物料+客户产品代号匹配已发布发布包(含子表, 供工单创建消费)
     * <p>匹配规则: ① 客户+物料+代号精确匹配取最新 RELEASED(有效期内) ;
     * ② 客户+物料默认包(is_default=1, 有效期内) ; ③ 无匹配返回 null。</p>
     *
     * @param clientId             客户ID
     * @param materialId           成品物料ID
     * @param customerProductCode  客户产品代号(可空)
     * @return 匹配的发布包详情, 无匹配返回 null
     */
    ReleasePackageVo matchPackage(Long clientId, Long materialId, String customerProductCode);

    /**
     * 按客户+物料查询所有已发布(RELEASED)发布包(前端选择列表)
     */
    List<ReleasePackageVo> selectByClientMaterial(Long clientId, Long materialId);

    // ==================== 审批流辅助 ====================

    /**
     * 查询可发起审批流程列表(mesReleasePackageService 已注册的自定义表单)
     */
    List<WfCustomForm> getFlows();

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     */
    String getProcInsId(String packageIdStr);
}
