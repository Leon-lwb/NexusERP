package org.dromara.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("bd_bom")
public class BdBom extends TenantEntity {

    @TableId(value = "bom_id")
    private Long bomId;

    private Long parentMaterialId;

    private String bomVersion;

    private String bomName;

    private Date effectiveDate;

    private Date expiryDate;

    private Long isCurrent;

    private Long status;

    private String remark;

    /**
     * 来源类型（PROCUREMENT-采购, PROCESS-加工）
     */
    private String sourceType;

    private String attr1;

    private String attr2;

    private Long attr3;

    private Long attr4;

    @TableLogic
    private String delFlag;
}
