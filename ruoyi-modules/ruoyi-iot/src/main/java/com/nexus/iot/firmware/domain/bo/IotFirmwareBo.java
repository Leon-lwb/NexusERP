package com.nexus.iot.firmware.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * IoT固件业务对象 iot_firmware。
 * <p>
 * 注意：{@code status}/{@code releaseTime} **不接受客户端写入**（服务端强制为 DRAFT，
 * 变更只能走 {@code PUT /iot/firmware/{id}/status} 的状态机端点）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IotFirmwareBo extends BaseEntity {

    /**
     * 固件ID（修改时必填）
     */
    private Long firmwareId;

    /**
     * 固件名称
     */
    @NotBlank(message = "固件名称不能为空")
    @Size(max = 128, message = "固件名称长度不能超过 128")
    private String firmwareName;

    /**
     * 适用产品型号（iot_product.product_code，空=不限）
     */
    @Size(max = 64, message = "适用产品型号长度不能超过 64")
    private String productKey;

    /**
     * 适用硬件型号（空=不限，仅登记）
     */
    @Size(max = 64, message = "适用硬件型号长度不能超过 64")
    private String hardwareModel;

    /**
     * 版本号
     */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 64, message = "版本号长度不能超过 64")
    private String version;

    /**
     * 固件包OSS文件ID（复用 ruoyi-common-oss 上传端点获得）
     */
    @NotBlank(message = "固件包OSS文件ID不能为空")
    @Size(max = 64, message = "固件包OSS文件ID长度不能超过 64")
    private String fileOssId;

    /**
     * 固件包文件名
     */
    @Size(max = 255, message = "固件包文件名长度不能超过 255")
    private String fileName;

    /**
     * 固件包大小(字节)
     */
    private Long fileSize;

    /**
     * 校验算法: MD5/SHA256（空则默认 MD5）
     */
    @Pattern(regexp = "MD5|SHA256", message = "校验算法仅支持 MD5/SHA256")
    private String checksumType;

    /**
     * 校验值(小写十六进制)
     */
    @NotBlank(message = "校验值不能为空")
    @Size(max = 128, message = "校验值长度不能超过 128")
    private String checksum;

    /**
     * 变更说明
     */
    @Size(max = 500, message = "变更说明长度不能超过 500")
    private String changeLog;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /**
     * 查询用：发布状态（仅查询条件，不参与写入）
     */
    private String status;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
