package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdVendor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 供应商视图对象 bd_vendor
 *
 * @author nexus
 * @date 2025-09-22
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdVendor.class)
public class BdVendorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 供应商ID
     */
    @ExcelProperty(value = "供应商ID")
    private Long vendorId;

    /**
     * 供应商编码
     */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /**
     * 税号
     */
    private String taxId;

    /**
     * 地址
     */
    private String address;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 电话
     */
    private String phone;

    /**
     * 银行账户
     */
    private String bankAccount;

    /**
     * 行业
     */
    @ExcelProperty(value = "行业")
    private String industry;

    /**
     * 地区
     */
    @ExcelProperty(value = "地区")
    private String region;

    /**
     * 分类 (原材料/标准件/服务类)
     */
    private String category;

    /**
     * 合作状态
     */
    @ExcelProperty(value = "合作状态")
    private String status;

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

    /** 采购员ID（关联sys_user） */
    private Long buyerId;

    /** 采购员名称 */
    private String buyerName;

    /** 归属部门ID（关联sys_dept） */
    private Long deptId;

    /** 归属部门名称 */
    private String deptName;

}
