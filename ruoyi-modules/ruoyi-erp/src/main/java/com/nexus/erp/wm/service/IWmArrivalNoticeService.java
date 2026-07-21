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

    /**
     * 分页查询到货通知列表
     *
     * @param bo 到货通知业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmArrivalNoticeVo> selectPageList(WmArrivalNoticeBo bo, PageQuery pageQuery);

    /**
     * 查询到货通知列表
     *
     * @param bo 到货通知业务对象（BO）
     * @return 结果列表
     */
    List<WmArrivalNoticeVo> selectList(WmArrivalNoticeBo bo);

    /**
     * 查询到货通知
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmArrivalNoticeVo selectById(Long id);

    /**
     * 新增到货通知
     *
     * @param bo 到货通知业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmArrivalNoticeBo bo);

    /**
     * 修改到货通知
     *
     * @param bo 到货通知业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmArrivalNoticeBo bo);

    /**
     * 删除到货通知
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
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
