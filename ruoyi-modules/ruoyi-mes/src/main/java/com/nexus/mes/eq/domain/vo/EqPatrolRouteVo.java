package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqPatrolRoute;

import java.io.Serial;
import java.io.Serializable;

/**
 * 属于MES 设备管理模块，巡检路线视图对象（查询出参）。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqPatrolRoute.class)
public class EqPatrolRouteVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long routeId;
    @ExcelProperty("路线名称")
    private String routeName;
    @ExcelProperty("描述")
    private String description;
    private String equipmentIds;
    private String checkItems;
    @ExcelProperty("频率")
    private String frequency;
    @ExcelProperty("状态")
    private String status;
    private String remark;
}
