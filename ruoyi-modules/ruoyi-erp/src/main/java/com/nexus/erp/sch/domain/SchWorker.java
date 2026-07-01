package com.nexus.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 生产工人档案对象 sch_worker
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_worker")
public class SchWorker extends TenantEntity {

    /**
     * 工人档案ID
     */
    @TableId(value = "worker_id")
    private Long workerId;
    /**
     * 关联的系统用户ID
     */
    @ExcelProperty("关联的系统用户ID")
    private Long userId;
    /**
     * 所属车间ID（关联bd_workshop）
     */
    @ExcelProperty("所属车间ID")
    private Long workshopId;
    /**
     * 所属车间名称
     */
    @ExcelProperty("所属车间名称")
    private String workshopName;
    /**
     * 员工姓名
     */
    @ExcelProperty("员工姓名")
    private String userName;
    /**
     * 员工工号
     */
    @ExcelProperty("员工工号")
    private String employeeId;
    /**
     * 手机号码
     */
    @ExcelProperty("手机号码")
    private String phonenumber;
    /**
     * 性别
     */
    @ExcelProperty("性别")
    private String sex;
    /**
     * 工种
     */
    @ExcelProperty("工种")
    private String workCategory;
    /**
     * 技能等级
     */
    @ExcelProperty("技能等级")
    private String skillLevel;
    /**
     * 在岗状态
     */
    @ExcelProperty("在岗状态")
    private String workStatus;
    /**
     * 入职日期
     */
    @ExcelProperty("入职日期")
    private Date entryDate;
    /**
     * 转正日期
     */
    @ExcelProperty("转正日期")
    private Date regularizationDate;

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
