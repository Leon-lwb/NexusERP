package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchTeam;

/**
 * 班组设置业务对象 sch_team
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchTeam.class, reverseConvertGenerate = false)
public class SchTeamBo extends BaseEntity {

    /**
     * 班组ID
     */
    private Long teamId;

    /**
     * 班组编码
     */
    private String teamCode;

    /**
     * 班组名称
     */
    @NotBlank(message = "班组名称不能为空")
    private String teamName;

    /**
     * 班组类别
     */
    private String teamCategory;

    /**
     * 所属车间ID
     */
    private Long workshopId;

    /**
     * 所属车间名称
     */
    private String workshopName;

    /**
     * 班组长ID
     */
    private Long leaderId;

    /**
     * 班组长姓名
     */
    private String leaderName;

    /**
     * 成员数量
     */
    private Long memberCount;

    /**
     * 状态
     */
    private Long status;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
