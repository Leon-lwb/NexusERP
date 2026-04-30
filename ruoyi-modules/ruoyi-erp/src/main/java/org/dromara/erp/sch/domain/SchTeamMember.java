package org.dromara.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 班组成员对象 sch_team_member
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_team_member")
public class SchTeamMember extends TenantEntity {

    /**
     * 成员ID
     */
    @TableId(value = "member_id")
    private Long memberId;

    /**
     * 班组ID
     */
    @ExcelProperty("班组ID")
    private Long teamId;

    /**
     * 人员ID
     */
    @ExcelProperty("人员ID")
    private Long workerId;

    /**
     * 人员姓名
     */
    @ExcelProperty("人员姓名")
    private String workerName;

    /**
     * 工号
     */
    @ExcelProperty("工号")
    private String workerCode;

    /**
     * 角色
     */
    @ExcelProperty("角色")
    private String role;

    /**
     * 生效日期
     */
    @ExcelProperty("生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty("失效日期")
    private Date expireDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Long status;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
