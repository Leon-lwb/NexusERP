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
@TableName("bd_vendor")
public class BdVendor extends TenantEntity {

    @TableId(value = "vendor_id")
    private Long vendorId;

    private String vendorCode;
    private String vendorName;
    private String taxId;
    private String address;
    private String contactPerson;
    private String phone;
    private String bankAccount;
    private String industry;
    private String region;
    private String category;
    private String status;
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
