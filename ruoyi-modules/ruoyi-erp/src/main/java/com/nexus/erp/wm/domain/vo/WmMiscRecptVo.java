package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson2.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 杂项入库单视图对象 wm_misc_recpt
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmMiscRecpt.class)
public class WmMiscRecptVo implements Serializable {

    /**
     * 入库单ID
     */
    @ExcelProperty("杂项入库ID")
    private Long recptId;

    /**
     * 入库单号
     */
    @ExcelProperty("入库单号")
    private String recptCode;

    /**
     * 入库类型
     */
    @ExcelProperty("入库类型")
    private String recptType;

    /**
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 库区ID
     */
    @ExcelProperty("库区ID")
    private Long areaId;

    /**
     * 库区编码
     */
    @ExcelProperty("库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @ExcelProperty("库区名称")
    private String areaName;

    /**
     * 入库日期
     */
    @ExcelProperty("入库日期")
    private Date recptDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;

    /**
     * 杂项入库单行项目列表
     */
    @JSONField(serialize = false)
    private List<WmMiscRecptLine> wmMiscRecptLineList;
}
