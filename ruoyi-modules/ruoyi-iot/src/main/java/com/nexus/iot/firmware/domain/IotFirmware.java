package com.nexus.iot.firmware.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * IoT固件登记对象 iot_firmware（审计 §4「功能缺失清单」#1 OTA 固件升级 MVP）。
 * <p>
 * 一条记录 = 一个可下发给设备的固件包：
 * <ul>
 *   <li>范围：{@code productKey}（对应 {@code iot_product.product_code}，空=不限）与
 *       {@code hardwareModel}（适用硬件型号，空=不限，**仅登记**：{@code iot_device} 无硬件型号列，
 *       因此不参与下发匹配，见报告「未完成项」）；</li>
 *   <li>包体：{@code fileOssId}（{@code ruoyi-common-oss} 上传后返回的文件ID）、{@code fileName}、
 *       {@code fileSize} 与校验值 {@code checksumType}/{@code checksum}（MD5 或 SHA256）——
 *       校验值随升级指令下发，设备侧下载后自行校验，平台侧不做包体存储；</li>
 *   <li>发布状态机：{@link IotFirmwareStatus#DRAFT} → {@link IotFirmwareStatus#RELEASED}
 *       → {@link IotFirmwareStatus#DISABLED}（可再发布）；**仅 RELEASED 可下发给设备**。</li>
 * </ul>
 * 唯一键 {@code uk_version(tenant_id, product_key, version)}：同租户、同产品型号下版本号唯一，
 * 防止"同一版本两个包"导致设备升级结果不可追溯。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_firmware")
public class IotFirmware extends TenantEntity {

    /**
     * 固件ID
     */
    @TableId(value = "firmware_id")
    private Long firmwareId;

    /**
     * 固件名称
     */
    private String firmwareName;

    /**
     * 适用产品型号（iot_product.product_code，空=不限）
     */
    private String productKey;

    /**
     * 适用硬件型号（空=不限，仅登记不参与匹配）
     */
    private String hardwareModel;

    /**
     * 版本号
     */
    private String version;

    /**
     * 固件包OSS文件ID
     */
    private String fileOssId;

    /**
     * 固件包文件名
     */
    private String fileName;

    /**
     * 固件包大小(字节)
     */
    private Long fileSize;

    /**
     * 校验算法: MD5/SHA256
     */
    private String checksumType;

    /**
     * 校验值(小写十六进制)
     */
    private String checksum;

    /**
     * 发布状态: DRAFT/RELEASED/DISABLED
     */
    private String status;

    /**
     * 发布时间
     */
    private Date releaseTime;

    /**
     * 变更说明
     */
    private String changeLog;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志(0正常 1删除)
     */
    @TableLogic
    private String delFlag;

}
