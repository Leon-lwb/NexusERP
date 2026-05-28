package com.nexus.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.sch.domain.SchTeam;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班组设置视图对象 sch_team
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchTeam.class)
public class SchTeamVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 班组ID
     */
    @ExcelProperty(value = "班组ID")
    private Long teamId;

    /**
     * 班组编码
     */
    @ExcelProperty(value = "班组编码")
    private String teamCode;

    /**
     * 班组名称
     */
    @ExcelProperty(value = "班组名称")
    private String teamName;

    /**
     * 班组类别
     */
    @ExcelProperty(value = "班组类别")
    private String teamCategory;

    /**
     * 所属车间ID
     */
    @ExcelProperty(value = "所属车间ID")
    private Long workshopId;

    /**
     * 所属车间名称
     */
    @ExcelProperty(value = "所属车间名称")
    private String workshopName;

    /**
     * 班组长ID
     */
    @ExcelProperty(value = "班组长ID")
    private Long leaderId;

    /**
     * 班组长姓名
     */
    @ExcelProperty(value = "班组长姓名")
    private String leaderName;

    /**
     * 成员数量
     */
    @ExcelProperty(value = "成员数量")
    private Long memberCount;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;
}
