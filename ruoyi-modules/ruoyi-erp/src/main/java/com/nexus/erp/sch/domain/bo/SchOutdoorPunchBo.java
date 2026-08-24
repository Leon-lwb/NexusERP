package com.nexus.erp.sch.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * App 外勤打卡入参对象
 *
 * @author nexus
 * @date 2026-08-24
 */
@Data
public class SchOutdoorPunchBo {

    /** 纬度 */
    @NotBlank(message = "纬度不能为空")
    private String latitude;

    /** 经度 */
    @NotBlank(message = "经度不能为空")
    private String longitude;

    /** 定位地址 */
    @NotBlank(message = "定位地址不能为空")
    private String address;

    /** 拜访客户名称(选填) */
    private String customerName;

    /** 外勤事由 */
    @NotBlank(message = "外勤事由不能为空")
    private String reason;

    /** 现场照片(base64，可带 data URI 前缀；打卡规则要求拍照时必填) */
    private String photoImage;
}
