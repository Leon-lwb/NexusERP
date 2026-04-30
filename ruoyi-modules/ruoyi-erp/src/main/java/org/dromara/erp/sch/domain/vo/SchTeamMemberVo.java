package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchTeamMember;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 班组成员视图对象 sch_team_member
 *
 * @author nexus
 * @date 2025-12-24
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchTeamMember.class)
public class SchTeamMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成员ID
     */
    @ExcelProperty(value = "成员ID")
    private Long memberId;

    /**
     * 班组ID
     */
    @ExcelProperty(value = "班组ID")
    private Long teamId;

    /**
     * 人员ID
     */
    @ExcelProperty(value = "人员ID")
    private Long workerId;

    /**
     * 人员姓名
     */
    @ExcelProperty(value = "人员姓名")
    private String workerName;

    /**
     * 工号
     */
    @ExcelProperty(value = "工号")
    private String workerCode;

    /**
     * 角色
     */
    @ExcelProperty(value = "角色")
    private String role;

    /**
     * 生效日期
     */
    @ExcelProperty(value = "生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty(value = "失效日期")
    private Date expireDate;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;
}
