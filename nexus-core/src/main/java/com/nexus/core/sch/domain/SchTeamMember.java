package com.nexus.core.sch.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 班组成员对象 sch_team_member
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchTeamMemberMapper.SchTeamMemberResult")
public class SchTeamMember extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 成员ID
     */
    @TableId(value = "member_id", type = IdType.AUTO)
    private Long memberId;

    /**
     * 班组ID
     */
    @Excel(name = "班组ID")
    private Long teamId;

    /**
     * 人员ID
     */
    @Excel(name = "人员ID")
    private Long workerId;

    /**
     * 人员姓名
     */
    @Excel(name = "人员姓名")
    private String workerName;

    /**
     * 工号
     */
    @Excel(name = "工号")
    private String workerCode;

    /**
     * 角色
     */
    @Excel(name = "角色")
    private String role;

    /**
     * 生效日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "失效日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date expireDate;

    /**
     * 状态
     */
    @Excel(name = "状态")
    private Long status;

}