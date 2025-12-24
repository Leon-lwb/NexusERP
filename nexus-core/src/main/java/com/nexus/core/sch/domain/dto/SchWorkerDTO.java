package com.nexus.core.sch.domain.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;

/**
 * 生产工人档案对象 sch_worker (不含params字段的DTO版本)
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("sch_worker")
public class SchWorkerDTO {
    private static final long serialVersionUID = 1L;

    /** 工人档案ID */
    @TableId(value = "worker_id", type = IdType.AUTO)
    private Long workerId;
    
    /** 关联的系统用户ID */
    @Excel(name = "关联的系统用户ID")
    private Long userId;
    
    /** 员工姓名 */
    @Excel(name = "员工姓名")
    private String userName;
    
    /** 员工工号 */
    @Excel(name = "员工工号")
    private String employeeId;
    
    /** 手机号码 */
    @Excel(name = "手机号码")
    private String phonenumber;
    
    /** 性别 */
    @Excel(name = "性别")
    private String sex;
    
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
    
    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 备注 */
    private String remark;
    
    // 不包含params字段，避免MyBatis Plus类型处理问题
}