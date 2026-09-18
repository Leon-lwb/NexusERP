package com.nexus.mes.ncr.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.ncr.domain.bo.NcrOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;

import java.math.BigDecimal;

/**
 * MES NCR不合格品单 Service 业务层接口
 * <p>
 * 状态机: OPEN -> REVIEW -> DISPOSITION -> CLOSED
 *                    └-> IN_REWORK -> CLOSED
 * 自动创建流程: 各质检模块或工序出口发现不合格时,通过 defectInfo JSON 传入缺陷明细,
 * 同时触发批次隔离和安灯质量告警(均为 best-effort,不阻断主流程)。
 * </p>
 *
 * @author Nexus
 */
public interface INcrOrderService {

    /** 状态: 待评审 */
    String STATUS_OPEN = "OPEN";
    /** 状态: 评审中 */
    String STATUS_REVIEW = "REVIEW";
    /** 状态: 待处置 */
    String STATUS_DISPOSITION = "DISPOSITION";
    /** 状态: 已关闭 */
    String STATUS_CLOSED = "CLOSED";
    /** 状态: 返工中(中间态,返工完成后自动关闭) */
    String STATUS_IN_REWORK = "IN_REWORK";

    /** 来源: 手工创建 */
    String SOURCE_CUSTOMER = "CUSTOMER";
    /** 来源: IQC来料检验 */
    String SOURCE_IQC = "IQC";
    /** 来源: IPQC过程检验 */
    String SOURCE_IPQC = "IPQC";
    /** 来源: OQC出货检验 */
    String SOURCE_OQC = "OQC";
    /** 来源: 工序出口 */
    String SOURCE_PROCESS = "PROCESS";

    /** 处置: 返工 */
    String DISPOSITION_REWORK = "REWORK";
    /** 处置: 报废 */
    String DISPOSITION_SCRAP = "SCRAP";
    /** 处置: 让步接收 */
    String DISPOSITION_ACCEPT = "ACCEPT_AS_IS";
    /** 处置: 退货 */
    String DISPOSITION_RETURN = "RETURN";

    /**
     * 分页查询NCR单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return NCR单分页数据
     */
    TableDataInfo<NcrOrderVo> selectPageList(NcrOrderBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取NCR单详情
     *
     * @param ncrId NCR单ID
     * @return NCR单详情
     */
    NcrOrderVo selectById(Long ncrId);

    /**
     * 新增NCR单(手工创建)
     *
     * @param bo NCR单业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(NcrOrderBo bo);

    /**
     * 更新NCR单
     *
     * @param bo NCR单业务对象
     * @return 更新成功返回 true
     */
    Boolean updateByBo(NcrOrderBo bo);

    /**
     * 删除NCR单
     *
     * @param ids NCR单ID数组
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 评审NCR单(OPEN -> REVIEW)
     *
     * @param ncrId NCR单ID
     * @return 评审成功返回 true
     */
    Boolean review(Long ncrId);

    /**
     * 处置NCR单(OPEN/REVIEW -> DISPOSITION, 按处置方式分支流转)
     *
     * @param ncrId       NCR单ID
     * @param disposition 处置方式(SCRAP/REWORK/ACCEPT/RETURN)
     * @param remark      处置备注(可空)
     * @return 处置成功返回 true
     */
    Boolean dispose(Long ncrId, String disposition, String remark);

    /**
     * 签署并处置NCR单(先落电子签名, 再执行处置, 同属一个事务)
     * <p>签署与处置必须同生共死：「已签署未处置」与「已处置未绑定签名」都是坏状态，
     * 故事务边界落在服务层，Controller 只做一次转发。</p>
     *
     * @param ncrId       NCR单ID
     * @param disposition 处置方式(REWORK/SCRAP/ACCEPT_AS_IS/RETURN)
     * @param remark      处置备注(可空)
     * @param authMethod  认证方式(LOGIN_ONLY 将被拒绝, 处置签署必须二级认证)
     * @param credential  认证凭证
     * @return 处置成功返回 true
     */
    Boolean disposeWithSignature(Long ncrId, String disposition, String remark,
                                 String authMethod, String credential);

    /**
     * 返工完成回调(IN_REWORK -> CLOSED)
     *
     * @param ncrId NCR单ID
     * @return 关闭成功返回 true
     */
    Boolean reworkCompleted(Long ncrId);

    /**
     * 绑定处置签名文件ID
     *
     * @param ncrId       NCR单ID
     * @param signatureId 签名文件ID(关联OSS)
     */
    void bindDispositionSignature(Long ncrId, Long signatureId);

    /**
     * NCR 处置签署：先签署再绑定，返回签名ID。
     *
     * @param ncrId      NCR 单ID
     * @param disposition 处置结论（并入签署含义）
     * @param authMethod  认证方式（不得为 LOGIN_ONLY）
     * @param credential  认证凭证
     * @return 签名ID
     */
    Long signDisposition(Long ncrId, String disposition, String authMethod, String credential);

    /**
     * IQC判定不合格时自动创建NCR单
     *
     * @param iqcId      IQC检验单ID
     * @param receiveId  收货单ID
     * @param materialId 物料ID
     * @param defectInfo 不良品信息(JSON)
     * @return NCR单视图对象
     */
    NcrOrderVo autoCreateFromIqc(Long iqcId, Long receiveId, Long materialId, String defectInfo);

    /**
     * IPQC判定不合格时自动创建NCR单
     *
     * @param ipqcId       IPQC检验单ID
     * @param mesWoId      MES工单ID
     * @param cardId       流转卡ID
     * @param stepRecordId 工序报工记录ID
     * @param processId    工序ID
     * @param defectQty    不良数量
     * @param defectInfo   不良品信息(JSON)
     * @return NCR单视图对象
     */
    NcrOrderVo autoCreateFromIpqc(Long ipqcId, Long mesWoId, Long cardId, Long stepRecordId, Long processId, BigDecimal defectQty, String defectInfo);

    /**
     * OQC判定不合格时自动创建NCR单
     *
     * @param oqcId      OQC检验单ID
     * @param soId       销售订单ID
     * @param materialId 物料ID
     * @param defectInfo 不良品信息(JSON)
     * @return NCR单视图对象
     */
    NcrOrderVo autoCreateFromOqc(Long oqcId, Long soId, Long materialId, String defectInfo);

    /**
     * 工序出站报工不良时自动创建NCR单
     *
     * @param cardId       流转卡ID
     * @param stepRecordId 工序报工记录ID
     * @param mesWoId      MES工单ID
     * @param defectQty    不良数量
     * @param defectInfo   不良品信息(JSON,可为空)
     * @return NCR单视图对象
     */
    NcrOrderVo autoCreateFromExit(Long cardId, Long stepRecordId, Long mesWoId, BigDecimal defectQty, String defectInfo);
}
