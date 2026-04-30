package org.dromara.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.List;

/**
 * 班组设置对象 sch_team
 *
 * @author nexus
 * @date 2025-12-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_team")
public class SchTeam extends TenantEntity {

    /**
     * 班组ID
     */
    @TableId(value = "team_id")
    private Long teamId;
    /**
     * 班组编码
     */
    @ExcelProperty("班组编码")
    private String teamCode;
    /**
     * 班组名称
     */
    @ExcelProperty("班组名称")
    private String teamName;
    /**
     * 班组类别
     */
    @ExcelProperty("班组类别")
    private String teamCategory;
    /**
     * 所属车间ID
     */
    @ExcelProperty("所属车间ID")
    private Long workshopId;
    /**
     * 所属车间名称
     */
    @ExcelProperty("所属车间名称")
    private String workshopName;
    /**
     * 班组长ID
     */
    @ExcelProperty("班组长ID")
    private Long leaderId;
    /**
     * 班组长姓名
     */
    @ExcelProperty("班组长姓名")
    private String leaderName;
    /**
     * 成员数量
     */
    @ExcelProperty("成员数量")
    private Long memberCount;
    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Long status;

    /**
     * 班组成员列表
     */
    @TableField(exist = false)
    private List<SchTeamMember> schTeamMemberList;

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
