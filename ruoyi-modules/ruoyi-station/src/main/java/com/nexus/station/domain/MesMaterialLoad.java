package com.nexus.station.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 工位物料上料台账对象 mes_material_load
 * <p>上料防错(M3 契约 C19)的台账: 工位×工序×工单×物料×批次维度留痕。
 * 同工单同工位同物料重复上料时, 旧 ACTIVE 记录置 REPLACED(换料语义)。
 * 校验基准为工单 MBOM 快照(pro_work_order_bom_line, 工单生命周期内不可变)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_material_load")
public class MesMaterialLoad extends TenantEntity {

    /** 台账ID(主键) */
    @TableId(value = "load_id")
    private Long loadId;

    /** 工单ID(pro_work_order.mes_wo_id) */
    private Long mesWoId;

    /** 工单编号 */
    private String woNo;

    /** 工位编码 */
    private String stationCode;

    /** 工序编码 */
    private String processCode;

    /** 工作中心编码(快照冗余) */
    private String workstationCode;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 批次ID(wm_batch.batch_id) */
    private Long batchId;

    /** 批次编码(wm_batch.batch_code) */
    private String batchCode;

    /** 料站号(可空, 贴片机 feeder 位号等) */
    private String feederSlot;

    /** 是否替代料匹配: Y=bd_bom_alternative 替代料, N=BOM 主料 */
    private String isAlternative;

    /** 上料人工号 */
    private String operatorCode;

    /** 上料人姓名 */
    private String operatorName;

    /** 上料时间 */
    private Date loadTime;

    /** 状态: ACTIVE=在机, REPLACED=已换料 */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
