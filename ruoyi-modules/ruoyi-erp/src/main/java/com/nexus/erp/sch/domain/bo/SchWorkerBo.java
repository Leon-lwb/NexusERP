package com.nexus.erp.sch.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.sch.domain.SchWorker;

import java.util.Date;

/**
 * 生产工人档案业务对象 sch_worker
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SchWorker.class, reverseConvertGenerate = false)
public class SchWorkerBo extends BaseEntity {

    /**
     * 工人档案ID
     */
    private Long workerId;

    /**
     * 关联的系统用户ID
     */
    private Long userId;

    /**
     * 所属车间ID（关联bd_workshop）
     */
    private Long workshopId;

    /**
     * 所属车间名称
     */
    private String workshopName;

    /**
     * 员工姓名
     */
    @NotBlank(message = "员工姓名不能为空")
    private String userName;

    /**
     * 员工工号
     */
    @NotBlank(message = "员工工号不能为空")
    private String employeeId;

    /**
     * 手机号码
     */
    private String phonenumber;

    /**
     * 性别
     */
    private String sex;

    /**
     * 工种
     */
    private String workCategory;

    /**
     * 技能等级
     */
    private String skillLevel;

    /**
     * 在岗状态
     */
    private String workStatus;

    /**
     * 入职日期
     */
    private Date entryDate;

    /**
     * 转正日期
     */
    private Date regularizationDate;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
