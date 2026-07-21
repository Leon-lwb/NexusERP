package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqPatrolRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 属于MES 设备管理模块，巡检记录视图对象（查询出参）。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqPatrolRecord.class)
public class EqPatrolRecordVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long recordId;
    private Long taskId;
    private Long routeId;
    private Long equipmentId;
    @ExcelProperty("设备编码")
    private String equipmentCode;
    @ExcelProperty("设备名称")
    private String equipmentName;
    @ExcelProperty("检查项目")
    private String checkItem;
    @ExcelProperty("实际值")
    private String actualValue;
    @ExcelProperty("结果")
    private String result;
    /** 巡检照片(OSS对象ID, 私有桶存ID不存URL) */
    private String photoOssId;
    /** 巡检照片访问地址(由 photoOssId 翻译; 私有桶返回临时签名URL) */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "photoOssId")
    private String photoUrl;
    @ExcelProperty("执行人")
    private String executorName;
    @ExcelProperty("执行时间")
    private Date executeTime;
    private String remark;
}
