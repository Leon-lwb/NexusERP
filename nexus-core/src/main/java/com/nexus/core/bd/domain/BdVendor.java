package com.nexus.core.bd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商对象 bd_vendor
 *
 * @author nexus
 * @date 2025-09-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("bd_vendor")
public class BdVendor extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 供应商ID */
    @TableId(value = "vendor_id",type = IdType.AUTO)
    private Long vendorId;
    /** 供应商编码 */
    @Excel(name = "供应商编码")
    private String vendorCode;
    /** 供应商名称 */
    @Excel(name = "供应商名称")
    private String vendorName;
    /** 税号 */
    private String taxId;
    /** 地址 */
    private String address;
    /** 联系人 */
    private String contactPerson;
    /** 电话 */
    private String phone;
    /** 银行账户 */
    private String bankAccount;
    /** 行业 */
    @Excel(name = "行业")
    private String industry;
    /** 地区 */
    @Excel(name = "地区")
    private String region;
    /** 分类 (原材料/标准件/服务类) */
    private String category;
    /** 合作状态  */
    @Excel(name = "合作状态 ")
    private String status;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}