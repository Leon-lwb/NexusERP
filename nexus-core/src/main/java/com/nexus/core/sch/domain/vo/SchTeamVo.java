
package com.nexus.core.sch.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

/**
 * 班组设置对象 sch_team vo
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
public class SchTeamVo extends BasePageDtoRequest {

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

}