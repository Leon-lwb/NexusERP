package org.dromara.erp.sch.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 节假日设置对象 sch_holiday
 *
 * @author nexus
 * @date 2025-12-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("sch_holiday")
public class SchHoliday extends TenantEntity {

    /**
     * 节假日ID
     */
    @TableId(value = "holiday_id")
    private Long holidayId;
    /**
     * 日期
     */
    @ExcelProperty("日期")
    private Date holidayDate;
    /**
     * 节假日名称
     */
    @ExcelProperty("节假日名称")
    private String holidayName;
    /**
     * 类型
     */
    @ExcelProperty("类型")
    private String holidayType;
    /**
     * 年份
     */
    @ExcelProperty("年份")
    private Long year;
    /**
     * 是否调休工作日
     */
    @ExcelProperty("是否调休工作日")
    private Integer isWorkingDay;

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
