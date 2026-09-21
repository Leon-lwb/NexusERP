package com.nexus.mes.dhr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * DHR 批记录主表对象 dhr_record。
 *
 * <p>状态机见 {@link DhrStatus}。提交/放行/驳回三个合规动作必须带电子签名
 * （{@code *_signature_id} 回填 es_signature 主键），签名与状态在同一请求内完成，
 * 失败即整单失败。</p>
 *
 * <p><b>数据源</b>：本表在 ry-mes，服务类标 {@code @DS("mes")}；而 {@code es_signature}
 * 只在 ry-vue(master)，两者的数据源边界处理见 {@code DhrRecordServiceImpl} 的类注释。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dhr_record")
public class DhrRecord extends TenantEntity {

    /** DHR 主键 */
    @TableId(value = "dhr_id")
    private Long dhrId;

    /** DHR 编号（租户内唯一） */
    private String dhrNo;

    /** MES 工单ID（pro_work_order.mes_wo_id） */
    private Long workOrderId;

    /** 工单编号（冗余快照） */
    private String workOrderNo;

    /** 产品物料ID */
    private Long materialId;

    /** 产品物料编码（冗余快照） */
    private String materialCode;

    /** 产品物料名称（冗余快照） */
    private String materialName;

    /** 批次号（可空） */
    private String lotNo;

    /** SN 序列号（可空） */
    private String sn;

    /** 计划数量 */
    private BigDecimal plannedQty;

    /** 完成数量 */
    private BigDecimal completedQty;

    /** 报废数量 */
    private BigDecimal scrapQty;

    /** 状态，见 {@link DhrStatus} */
    private String status;

    /** 快照总哈希：SM3(按 sort_order 升序拼接 section_code + '\n' + content_hash + '\n') */
    private String snapshotHash;

    /** 冻结时间（提交时写入） */
    private Date frozenAt;

    /** 冻结人 */
    private Long frozenBy;

    /** 提交时间 */
    private Date submittedAt;

    /** 提交人 */
    private Long submittedBy;

    /** 提交签名ID（es_signature.signature_id，biz_type=DHR_SUBMIT） */
    private Long submitSignatureId;

    /** 放行时间 */
    private Date releasedAt;

    /** 放行人 */
    private Long releasedBy;

    /** 放行签名ID（biz_type=DHR_RELEASE） */
    private Long releaseSignatureId;

    /** 驳回时间 */
    private Date rejectedAt;

    /** 驳回人 */
    private Long rejectedBy;

    /** 驳回原因 */
    private String rejectReason;

    /** 驳回签名ID（biz_type=DHR_REJECT） */
    private Long rejectSignatureId;

    /** 归档时间 */
    private Date archivedAt;

    /** 版本号（每次状态流转 +1） */
    private Integer version;

    /** 备注（构建入参） */
    private String remark;

    /** 删除标志（仅 DRAFT 可删） */
    @TableLogic
    private String delFlag;
}
