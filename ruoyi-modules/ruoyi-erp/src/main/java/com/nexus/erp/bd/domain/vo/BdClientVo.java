package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdClient;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户信息视图对象 bd_client
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdClient.class)
public class BdClientVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 客户ID
     */
    @ExcelProperty(value = "客户ID")
    private Long clientId;

    /**
     * 客户编码
     */
    @ExcelProperty(value = "客户编码")
    private String clientCode;

    /**
     * 客户名称
     */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /**
     * 客户简写
     */
    @ExcelProperty(value = "客户简写")
    private String clientNick;

    /**
     * 客户英文名称
     */
    @ExcelProperty(value = "客户英文名称")
    private String clientEn;

    /**
     * 客户简介
     */
    @ExcelProperty(value = "客户简介")
    private String clientDesc;

    /**
     * 客户类型
     */
    @ExcelProperty(value = "客户类型")
    private String clientType;

    /**
     * 客户LOGO
     */
    @ExcelProperty(value = "客户LOGO")
    private String clientLogo;

    /**
     * 客户默认送货地点
     */
    private String address;

    /**
     * 客户官网地址
     */
    private String webSite;

    /**
     * 客户邮箱
     */
    private String email;

    /**
     * 客户电话
     */
    @ExcelProperty(value = "客户电话")
    private String tel;

    /**
     * 联系人1
     */
    private String contact1;

    /**
     * 联系人1电话
     */
    private String contact1Tel;

    /**
     * 联系人1邮箱
     */
    private String contact1Emal;

    /**
     * 联系人2
     */
    private String contact2;

    /**
     * 联系人2电话
     */
    private String contact2Tel;

    /**
     * 联系人2邮箱
     */
    private String contact2Emal;

    /**
     * 统一社会信用代码
     */
    @ExcelProperty(value = "统一社会信用代码")
    private String creditCode;

    /**
     * 省份行政区划ID
     */
    private Long provinceAreaId;

    /**
     * 城市行政区划ID
     */
    private Long cityAreaId;

    /**
     * 区县行政区划ID
     */
    private Long districtAreaId;

    /**
     * 详细地址(街道、门牌号等)
     */
    private String streetId;

    /**
     * 地址纬度
     */
    private String addressLat;

    /**
     * 地址经度
     */
    private String addressLng;

}
