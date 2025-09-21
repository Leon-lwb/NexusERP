package com.nexus.bd.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 客户信息对象 bd_client
 *
 * @author nexus
 * @date 2025-09-18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("bd_client")
public class BdClient extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 客户ID */
    @TableId(value = "client_id",type = IdType.AUTO)
    private Long clientId;
    /** 客户编码 */
    @Excel(name = "客户编码")
    private String clientCode;
    /** 客户名称 */
    @Excel(name = "客户名称")
    private String clientName;
    /** 客户简写 */
    @Excel(name = "客户简写")
    private String clientNick;
    /** 客户英文名称 */
    @Excel(name = "客户英文名称")
    private String clientEn;
    /** 客户简介 */
    @Excel(name = "客户简介")
    private String clientDesc;
    /** 客户类型 */
    @Excel(name = "客户类型")
    private String clientType;
    /** 客户LOGO */
    @Excel(name = "客户LOGO")
    private String clientLogo;
    /** 客户默认送货地点 */
    private String address;
    /** 客户官网地址 */
    private String webSite;
    /** 客户邮箱 */
    private String email;
    /** 客户电话 */
    @Excel(name = "客户电话")
    private String tel;
    /** 联系人1 */
    private String contact1;
    /** 联系人1电话 */
    private String contact1Tel;
    /** 联系人1邮箱 */
    private String contact1Emal;
    /** 联系人2 */
    private String contact2;
    /** 联系人2电话 */
    private String contact2Tel;
    /** 联系人2邮箱 */
    private String contact2Emal;
    /** 统一社会信用代码 */
    @Excel(name = "统一社会信用代码")
    private String creditCode;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}