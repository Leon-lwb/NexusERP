package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_misc_issue")
public class WmMiscIssue extends TenantEntity {

    @TableId(value = "issue_id")
    private Long issueId;

    private String issueCode;
    private String issueType;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Date issueDate;
    private String status;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableField(exist = false)
    private List<WmMiscIssueLine> wmMiscIssueLineList;

    @TableLogic
    private String delFlag;
}
