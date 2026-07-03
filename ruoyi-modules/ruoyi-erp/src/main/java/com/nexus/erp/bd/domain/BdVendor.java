package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 供应商信息对象 bd_vendor
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_vendor")
public class BdVendor extends TenantEntity {

    /**
     * 供应商ID
     */
    @TableId(value = "vendor_id")
    private Long vendorId;

    /**
     * 供应商编码
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    private String vendorName;

    /**
     * 纳税人识别号
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
     * 联系电话
     */
    private String phone;

    /**
     * 银行账号
     */
    private String bankAccount;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 所属地区
     */
    private String region;

    /**
     * 供应商类别
     */
    private String category;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

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
     * 省份区域ID
     */
    private Long provinceAreaId;

    /**
     * 城市区域ID
     */
    private Long cityAreaId;

    /**
     * 区县区域ID
     */
    private Long districtAreaId;

    /**
     * 街道ID
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
