package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 客户信息对象 bd_client
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_client")
public class BdClient extends TenantEntity {

    /**
     * 客户ID
     */
    @TableId(value = "client_id")
    private Long clientId;

    /**
     * 客户编码
     */
    private String clientCode;

    /**
     * 客户名称
     */
    private String clientName;

    /**
     * 客户简称
     */
    private String clientNick;

    /**
     * 客户英文名称
     */
    private String clientEn;

    /**
     * 客户描述
     */
    private String clientDesc;

    /**
     * 客户类型
     */
    private String clientType;

    /**
     * 客户Logo
     */
    private String clientLogo;

    /**
     * 地址
     */
    private String address;

    /**
     * 网站
     */
    private String webSite;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 电话
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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
