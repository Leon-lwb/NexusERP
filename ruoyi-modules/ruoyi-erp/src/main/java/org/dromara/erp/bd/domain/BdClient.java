package org.dromara.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_client")
public class BdClient extends TenantEntity {

    @TableId(value = "client_id")
    private Long clientId;

    private String clientCode;
    private String clientName;
    private String clientNick;
    private String clientEn;
    private String clientDesc;
    private String clientType;
    private String clientLogo;
    private String address;
    private String webSite;
    private String email;
    private String tel;
    private String contact1;
    private String contact1Tel;
    private String contact1Emal;
    private String contact2;
    private String contact2Tel;
    private String contact2Emal;
    private String creditCode;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;
    private Long provinceAreaId;
    private Long cityAreaId;
    private Long districtAreaId;
    private String streetId;
    private String addressLat;
    private String addressLng;

    @TableLogic
    private String delFlag;
}
