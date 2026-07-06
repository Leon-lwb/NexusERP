package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdVendor;

/**
 * 供应商业务对象 bd_vendor
 *
 * @author nexus
 * @date 2025-09-22
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdVendor.class, reverseConvertGenerate = false)
public class BdVendorBo extends BaseEntity {

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
     * <p>
     * 自动生成开关打开时由前端清空、后端兜底生成（避免预览消耗流水号），故不强制非空。
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    @NotBlank(message = "供应商名称不能为空")
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
    private String industry;

    /**
     * 地区
     */
    private String region;

    /**
     * 分类 (原材料/标准件/服务类)
     */
    private String category;

    /**
     * 合作状态
     */
    private String status;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

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

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
