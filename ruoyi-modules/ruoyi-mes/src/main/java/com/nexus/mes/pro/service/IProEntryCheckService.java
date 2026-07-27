package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.bo.ProEntryBo;
import com.nexus.mes.pro.domain.bo.ProExitBo;
import com.nexus.mes.pro.domain.vo.ProCheckResultVo;

/**
 * MES工序进站/出站校验规则引擎 Service 接口(P0-3)
 * <p>按 {@code pro_step_check_rule} 配置执行工序进站(ENTRY)/出站(EXIT)校验，
 * 收集未通过项明细返回。{@code entry/exit} 编排方法据此决定是否放行状态流转。</p>
 *
 * <p>校验类型(check_type)：</p>
 * <ul>
 *   <li>ENTRY 阶段：SERIAL_GATE(内置,always-on)/SOP_VERSION/TOOLING_AVAILABLE/LOT_AVAILABLE/EQUIPMENT_STATUS
 *       /MATERIAL_KITTING(存根)/OPERATOR_SKILL(存根)</li>
 *   <li>EXIT 阶段：QUANTITY_REACHED/INSPECTION_REQUIRED/EXCEPTION_CLOSED/PARAMETER_OK(存根)</li>
 * </ul>
 *
 * @author Nexus
 */
public interface IProEntryCheckService {

    /** 校验阶段: 进站 */
    String STAGE_ENTRY = "ENTRY";
    /** 校验阶段: 出站 */
    String STAGE_EXIT = "EXIT";

    /** 校验类型: 串行门禁(内置,上一工序须完成) */
    String TYPE_SERIAL_GATE = "SERIAL_GATE";
    /** 校验类型: SOP版本(须已发布) */
    String TYPE_SOP_VERSION = "SOP_VERSION";
    /** 校验类型: 工装可用性 */
    String TYPE_TOOLING_AVAILABLE = "TOOLING_AVAILABLE";
    /** 校验类型: 批次可用性 */
    String TYPE_LOT_AVAILABLE = "LOT_AVAILABLE";
    /** 校验类型: 设备状态(须NORMAL) */
    String TYPE_EQUIPMENT_STATUS = "EQUIPMENT_STATUS";
    /** 校验类型: 物料齐套(存根,待P0-1) */
    String TYPE_MATERIAL_KITTING = "MATERIAL_KITTING";
    /** 校验类型: 人员资质(存根,待资质表) */
    String TYPE_OPERATOR_SKILL = "OPERATOR_SKILL";
    /** 校验类型: 数量达标(出站,good+defect+scrap=planned) */
    String TYPE_QUANTITY_REACHED = "QUANTITY_REACHED";
    /** 校验类型: 检验完成(出站,关键工序IPQC PASS) */
    String TYPE_INSPECTION_REQUIRED = "INSPECTION_REQUIRED";
    /** 校验类型: 异常已关闭(出站,无活跃安灯) */
    String TYPE_EXCEPTION_CLOSED = "EXCEPTION_CLOSED";
    /** 校验类型: 工艺参数达标(出站,存根,待dc) */
    String TYPE_PARAMETER_OK = "PARAMETER_OK";

    /** 启用 */
    String ENABLED = "1";

    /**
     * 执行进站校验(ENTRY 规则 + 内置 SERIAL_GATE)
     *
     * @param stepRecordId 工序记录ID
     * @param context      进站上下文(含操作员/工装/批次,可空字段对应的规则跳过)
     * @return 校验结果(passed=true 表示全部通过)
     */
    ProCheckResultVo runEntryChecks(Long stepRecordId, ProEntryBo context);

    /**
     * 执行出站校验(EXIT 规则)
     *
     * @param stepRecordId 工序记录ID
     * @param context      出站上下文(含良品/不良/报废数量)
     * @return 校验结果
     */
    ProCheckResultVo runExitChecks(Long stepRecordId, ProExitBo context);
}
