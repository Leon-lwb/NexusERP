package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdClient;

/**
 * 客户信息业务对象 bd_client
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdClient.class, reverseConvertGenerate = false)
public class BdClientBo extends BaseEntity {

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户编码（为空时由后端按 CLIENT_CODE 规则自动生成）
     */
    private String clientCode;

    /**
     * 客户名称
     */
    @NotBlank(message = "客户名称不能为空")
    private String clientName;

    /**
     * 客户简写
     */
    private String clientNick;

    /**
     * 客户英文名称
     */
    private String clientEn;

    /**
     * 客户简介
     */
    private String clientDesc;

    /**
     * 客户类型
     */
    private String clientType;

    /**
     * 客户LOGO
     */
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
    private String creditCode;

    /** 纳税人识别号 */
    private String taxId;
    /** 发票抬头 */
    private String invoiceTitle;
    /** 开户行 */
    private String invoiceBankName;
    /** 银行账号 */
    private String invoiceBankAccount;
    /** 开票地址、电话 */
    private String invoiceAddressPhone;
    /** 默认发票类型 VAT_SPECIAL/VAT_NORMAL/OTHER */
    private String defaultInvoiceType;

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

    /** 业务员ID（关联sys_user） */
    private Long salesRepId;

    /** 业务员名称 */
    private String salesRepName;

    /** 归属部门ID（关联sys_dept） */
    private Long deptId;

    /** 归属部门名称 */
    private String deptName;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
