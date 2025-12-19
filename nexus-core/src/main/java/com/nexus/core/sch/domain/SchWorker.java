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
 * 生产工人档案对象 sch_worker
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.sch.mapper.SchWorkerMapper.SchWorkerResult")
public class SchWorker extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 工人档案ID */
    @TableId(value = "worker_id",type = IdType.AUTO)
    private Long workerId;
    /** 关联的系统用户ID */
    @Excel(name = "关联的系统用户ID")
    private Long userId;
    /** 员工工号 */
    @Excel(name = "员工工号")
    private String employeeId;
    /** 工种 */
    @Excel(name = "工种")
    private String workCategory;
    /** 技能等级 */
    @Excel(name = "技能等级")
    private String skillLevel;
    /** 在岗状态 */
    @Excel(name = "在岗状态")
    private String workStatus;
    /** 入职日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "入职日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date entryDate;
    /** 转正日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "转正日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date regularizationDate;
}