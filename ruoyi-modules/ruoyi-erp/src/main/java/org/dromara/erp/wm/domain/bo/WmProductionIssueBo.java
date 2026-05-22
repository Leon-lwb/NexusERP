package org.dromara.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.wm.domain.WmProductionIssue;
import org.dromara.erp.wm.domain.WmProductionIssueLine;

import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmProductionIssue.class)
public class WmProductionIssueBo extends BaseEntity {

    @NotNull(message = "生产领料ID不能为空", groups = {EditGroup.class})
    private Long issueId;

    @NotBlank(message = "领料单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String issueCode;

    private Long woId;
    private String woNo;
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

    private List<WmProductionIssueLine> wmProductionIssueLineList;
}
