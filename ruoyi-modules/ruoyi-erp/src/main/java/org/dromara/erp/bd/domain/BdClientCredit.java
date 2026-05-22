package org.dromara.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.tenant.core.TenantEntity;

@Data
@TableName("bd_client_credit")
public class BdClientCredit extends TenantEntity {

    @TableId(value = "credit_id")
    private Long creditId;

    private Long clientId;
    private String clientCode;
    private String clientName;
    private Integer clientCredit;
    private Integer clientTime;
    private String clientLevel;
    private Integer clientScore;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;
}
