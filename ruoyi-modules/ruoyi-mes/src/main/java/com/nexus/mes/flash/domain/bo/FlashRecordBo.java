package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.flash.domain.FlashRecord;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES固件刷写记录业务对象 mes_flash_record
 * <p>M2 刷写作业: 补录(insertByBo)宽松自动回填, 作业确认(confirmFlash)严格校验:
 * SN 须存在且非报废, 固件须已发布(RELEASED)。同一 SN 允许多条(FAIL 重刷保留历史, 判定取最新一条)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FlashRecord.class)
public class FlashRecordBo extends BaseEntity {

    /** 刷写记录ID */
    @Null(groups = {AddGroup.class})
    @NotNull(groups = {EditGroup.class})
    private Long flashRecordId;

    /** 刷写记录号(查询用;新增/确认自动生成 MES_FLASH_NO = FL+yyyyMMdd+日流水) */
    private String flashNo;

    /** 刷写类型标识(固件类=firmware_file; 缺省按firmware_file路由) */
    private String flashTypeCode;

    /** SN ID */
    private Long snId;

    /** SN编码(确认必填, 需存在于 pro_sn) */
    @NotBlank(message = "SN编码不能为空", groups = {AddGroup.class})
    private String snCode;

    /** MES工单ID */
    private Long mesWoId;

    /** 工单号 */
    private String woNo;

    /** 流转卡ID */
    private Long cardId;

    /** 流转卡工序记录ID(pro_flow_card_step) */
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 工序名称 */
    private String processName;

    /** 内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL(固件类=FIRMWARE, 可不传由Provider定) */
    private String contentRefType;

    /** 内容源记录ID(固件类=mes_firmware_id; 确认必填) */
    @NotNull(message = "刷写内容不能为空", groups = {AddGroup.class})
    private Long contentRefId;

    /** 内容版本(由Provider回填) */
    private String contentVersion;

    /** 实际刷写内容校验值(固件类=MD5) */
    private String contentChecksum;

    /** 实际写入内容JSON(池类, 如 {"mac":"00:1A:2B:3C:4D:5E"}) */
    private String contentPayload;

    /** 刷写设备ID(eq_equipment) */
    private Long equipmentId;

    /** 刷写设备编码(由设备ID回填) */
    private String equipmentCode;

    /** 操作人ID(查询用;新增默认当前登录人) */
    private Long operatorId;

    /** 操作人姓名(查询用) */
    private String operatorName;

    /** 刷写时间(新增默认当前时间) */
    private Date flashTime;

    /** 刷写时间区间-起(查询用) */
    private Date beginFlashTime;

    /** 刷写时间区间-止(查询用) */
    private Date endFlashTime;

    /** 结果: PASS/FAIL(确认必填) */
    @NotBlank(message = "刷写结果不能为空", groups = {AddGroup.class})
    private String result;

    /** 失败原因 */
    private String failReason;

    /** 重试次数(默认0) */
    private Integer retryCount;

    /** 刷写模式: AUTO/MANUAL */
    private String flashMode;

    /** 备注 */
    private String remark;
}