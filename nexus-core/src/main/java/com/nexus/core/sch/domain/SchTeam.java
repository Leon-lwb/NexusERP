package com.nexus.core.sch.domain;

import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 班组设置对象 sch_team
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchTeamMapper.SchTeamResult")
public class SchTeam extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 班组ID
     */
    @TableId(value = "team_id", type = IdType.AUTO)
    private Long teamId;
    /**
     * 班组编码
     */
    @Excel(name = "班组编码")
    private String teamCode;
    /**
     * 班组名称
     */
    @Excel(name = "班组名称")
    private String teamName;
    /**
     * 班组类别
     */
    @Excel(name = "班组类别")
    private String teamCategory;
    /**
     * 所属车间ID
     */
    @Excel(name = "所属车间ID")
    private Long workshopId;
    /**
     * 所属车间名称
     */
    @Excel(name = "所属车间名称")
    private String workshopName;
    /**
     * 班组长ID
     */
    @Excel(name = "班组长ID")
    private Long leaderId;
    /**
     * 班组长姓名
     */
    @Excel(name = "班组长姓名")
    private String leaderName;
    /**
     * 成员数量
     */
    @Excel(name = "成员数量")
    private Long memberCount;
    /**
     * 状态
     */
    @Excel(name = "状态")
    private Long status;

    private List<SchTeamMember> schTeamMemberList;

    public List<SchTeamMember> getSchTeamMemberList()
    {
        return schTeamMemberList;
    }

    public void setSchTeamMemberList(List<SchTeamMember> schTeamMemberList)
    {
        this.schTeamMemberList = schTeamMemberList;
    }

}