package com.nexus.mes.fw.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.fw.domain.FwFirmware;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES固件档案视图对象 fw_firmware
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FwFirmware.class)
public class FwFirmwareVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 固件ID */
    @ExcelProperty("固件ID")
    private Long firmwareId;

    /** 固件编码 */
    @ExcelProperty("固件编码")
    private String firmwareCode;

    /** 固件名称 */
    @ExcelProperty("固件名称")
    private String firmwareName;

    /** 适用物料ID */
    private Long materialId;

    /** 适用物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 适用物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 版本号 */
    @ExcelProperty("版本号")
    private String version;

    /** 构建号 */
    private String buildNo;

    /** 固件文件OSS文件ID(私有桶存ID) */
    private Long ossId;

    /** 固件文件访问地址(由 ossId 翻译; 私有桶返回临时签名URL) */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "ossId")
    private String fileUrl;

    /** 固件文件MD5 */
    @ExcelProperty("文件MD5")
    private String fileMd5;

    /** 固件文件大小(字节) */
    @ExcelProperty("文件大小")
    private Long fileSize;

    /** 发布说明 */
    private String releaseNote;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED */
    @ExcelProperty("状态")
    private String status;

    /** 是否物料默认版本(0否/1是) */
    private String isDefault;

    /** 审批流配置ID(wf_custom_form.id) */
    private Long selectFlowId;

    /** 发布人ID */
    private Long publishedBy;

    /** 发布人姓名(由 publishedBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "publishedBy")
    private String publishedByName;

    /** 发布时间 */
    private Date publishTime;

    /** 创建者ID */
    private Long createBy;

    /** 创建者姓名(由 createBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;
}