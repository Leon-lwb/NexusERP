package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchTeamMember;

import java.util.Date;

/**
 * 班组成员业务对象 sch_team_member
 *
 * @author nexus
 * @date 2025-12-24
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchTeamMember.class, reverseConvertGenerate = false)
public class SchTeamMemberBo extends BaseEntity {

    /**
     * 成员ID
     */
    private Long memberId;

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 人员ID
     */
    private Long workerId;

    /**
     * 人员姓名
     */
    private String workerName;

    /**
     * 工号
     */
    private String workerCode;

    /**
     * 角色
     */
    private String role;

    /**
     * 生效日期
     */
    private Date effectiveDate;

    /**
     * 失效日期
     */
    private Date expireDate;

    /**
     * 状态
     */
    private Long status;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
