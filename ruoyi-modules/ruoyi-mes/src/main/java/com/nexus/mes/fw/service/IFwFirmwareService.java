package com.nexus.mes.fw.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.fw.domain.bo.FwFirmwareBo;
import com.nexus.mes.fw.domain.vo.FwFirmwareVo;
import com.nexus.workflow.domain.WfCustomForm;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * MES固件档案 Service 接口
 * <p>状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED (Flowable 回调 + 管理员兜底)</p>
 *
 * @author Nexus
 */
public interface IFwFirmwareService {

    /** 状态-草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态-审批中 */
    String STATUS_IN_REVIEW = "IN_REVIEW";
    /** 状态-已发布 */
    String STATUS_RELEASED = "RELEASED";
    /** 状态-已归档 */
    String STATUS_ARCHIVED = "ARCHIVED";
    /** 是否默认-是 */
    String IS_DEFAULT_YES = "1";
    /** 是否默认-否 */
    String IS_DEFAULT_NO = "0";
    /** 审批业务服务名(与 @Service 注解及 wf_custom_form.business_service 一致) */
    String SERVICE_NAME = "fwFirmwareService";

    /**
     * 分页查询固件档案列表
     */
    TableDataInfo<FwFirmwareVo> selectPageList(FwFirmwareBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询固件详情
     */
    FwFirmwareVo selectById(Long firmwareId);

    /**
     * 新增固件(自动编码 MES_FW_CODE, 状态 DRAFT)
     */
    Boolean insertByBo(FwFirmwareBo bo);

    /**
     * 修改固件(仅 DRAFT/ARCHIVED 可编辑)
     */
    Boolean updateByBo(FwFirmwareBo bo);

    /**
     * 批量删除固件(仅 DRAFT 可删)
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 上传固件文件: MinIO 存储 + MD5/大小回填
     *
     * @param file 待上传文件
     * @return {ossId, fileName, fileSize, md5}
     */
    Map<String, Object> uploadFirmware(MultipartFile file);

    /**
     * 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable, 否则仅改状态)
     */
    Boolean submitForApproval(Long firmwareId, String selectFlowId);

    /**
     * 兜底审批通过: IN_REVIEW -> RELEASED
     */
    Boolean approve(Long firmwareId, String remark);

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    Boolean reject(Long firmwareId, String reason);

    /**
     * 归档: RELEASED -> ARCHIVED (被工序绑定/存在刷写记录时禁止)
     */
    Boolean archive(Long firmwareId, String reason);

    /**
     * 设为物料默认版本(仅 RELEASED, 物料内唯一)
     */
    Boolean setDefault(Long firmwareId);

    /**
     * 查询可发起审批流程列表(fwFirmwareService 已注册的自定义表单)
     */
    List<WfCustomForm> getFlows();

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     */
    String getProcInsId(String firmwareIdStr);
}