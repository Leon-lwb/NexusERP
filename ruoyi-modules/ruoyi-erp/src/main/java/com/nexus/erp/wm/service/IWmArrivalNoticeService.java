package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmArrivalNoticeBo;
import com.nexus.erp.wm.domain.vo.WmArrivalNoticeVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 到货通知Service接口
 *
 * @author nexus
 */
public interface IWmArrivalNoticeService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已通过 */
    String STATUS_APPROVED = "APPROVED";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";

    TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery);

    List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo);

    WmArrivalNoticeVo selectById(Long id);

    int insertByBo(WmArrivalNoticeBo bo);

    int updateByBo(WmArrivalNoticeBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 提交到货通知审批
     *
     * @param noticeIdStr  到货通知ID字符串
     * @param selectFlowId 选择的流程模板ID
     * @return 是否成功
     */
    boolean submit(String noticeIdStr, String selectFlowId);

    /**
     * 获取到货通知流程实例ID
     *
     * @param noticeIdStr 到货通知ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String noticeIdStr);
}
