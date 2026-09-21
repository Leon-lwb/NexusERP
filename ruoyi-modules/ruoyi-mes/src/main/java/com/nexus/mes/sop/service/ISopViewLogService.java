package com.nexus.mes.sop.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.sop.domain.bo.SopViewLogBo;
import com.nexus.mes.sop.domain.vo.SopViewLogVo;

/**
 * SOP查阅记录 Service 业务层接口
 * <p>signature_id 预留 (P0-6 ESignatureService 接入后回填)</p>
 *
 * @author Nexus
 */
public interface ISopViewLogService {

    /**
     * 分页查询查阅记录列表
     *
     * @param bo         查阅记录查询条件
     * @param pageQuery  分页参数
     * @return 分页列表
     */
    TableDataInfo<SopViewLogVo> selectPageList(SopViewLogBo bo, PageQuery pageQuery);

    /**
     * 登记查阅(查看器打开时调): viewer/time/wo/process/IP/UA, signature_id=null(预留P0-6)
     *
     * @param sopId         SOP ID
     * @param versionNo     查阅的版本号
     * @param woId          关联工单ID(可选)
     * @param woNo          工单编号(冗余,可选)
     * @param processId     关联工序ID(可选)
     * @param processName   工序名称(冗余,可选)
     * @param workstationId 工位ID(可选)
     * @return 登记成功返回 true
     */
    Boolean recordView(Long sopId, String versionNo, Long woId, String woNo,
                       Long processId, String processName, Long workstationId);

    /**
     * 批量删除查阅记录
     *
     * @param ids 查阅记录ID集合
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 操作员是否已「已阅确认」指定版本的 SOP(MES-SOP-02 门禁)。
     * <p>判定口径: 存在 {@code sop_view_log} 行且 {@code viewer_id=operatorId}、
     * {@code sop_id/version_no} 匹配、{@code signature_id} 非空(即真的产生了
     * {@code SOP_VIEW} 电子签名)。仅有查阅行而无签名不算确认。</p>
     *
     * @param sopId      SOP ID
     * @param versionNo  版本号(可空=不限版本)
     * @param operatorId 操作员ID
     * @return 已确认返回 true
     */
    boolean hasAcknowledged(Long sopId, String versionNo, Long operatorId);
}
