package org.dromara.erp.sch.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.sch.domain.SchWorker;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 生产工人档案视图对象 sch_worker
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SchWorker.class)
public class SchWorkerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工人档案ID
     */
    @ExcelProperty(value = "工人档案ID")
    private Long workerId;

    /**
     * 关联的系统用户ID
     */
    @ExcelProperty(value = "关联的系统用户ID")
    private Long userId;

    /**
     * 员工姓名
     */
    @ExcelProperty(value = "员工姓名")
    private String userName;

    /**
     * 员工工号
     */
    @ExcelProperty(value = "员工工号")
    private String employeeId;

    /**
     * 手机号码
     */
    @ExcelProperty(value = "手机号码")
    private String phonenumber;

    /**
     * 性别
     */
    @ExcelProperty(value = "性别")
    private String sex;

    /**
     * 工种
     */
    @ExcelProperty(value = "工种")
    private String workCategory;

    /**
     * 技能等级
     */
    @ExcelProperty(value = "技能等级")
    private String skillLevel;

    /**
     * 在岗状态
     */
    @ExcelProperty(value = "在岗状态")
    private String workStatus;

    /**
     * 入职日期
     */
    @ExcelProperty(value = "入职日期")
    private Date entryDate;

    /**
     * 转正日期
     */
    @ExcelProperty(value = "转正日期")
    private Date regularizationDate;
}
