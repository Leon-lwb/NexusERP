package com.nexus.mes.label.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.label.domain.MesLabelTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 标签打印模板视图对象 mes_label_template
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesLabelTemplate.class)
public class MesLabelTemplateVo implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    @ExcelProperty("模板ID")
    private Long templateId;

    /**
     * 模板编码
     */
    @ExcelProperty("模板编码")
    private String templateCode;

    /**
     * 模板修订号；旧客户端可不传，服务端仍按实际修订号保护并发保存
     */
    private Long templateRevision;

    /**
     * 模板名称
     */
    @ExcelProperty("模板名称")
    private String templateName;

    /**
     * 标签类型: SN
     */
    @ExcelProperty("标签类型")
    private String labelType;

    /**
     * 打印机语言: TSPL/ZPL
     */
    @ExcelProperty("打印机语言")
    private String labelLang;

    /**
     * 标签宽度(mm)
     */
    @ExcelProperty("标签宽度(mm)")
    private Integer widthMm;

    /**
     * 标签高度(mm)
     */
    @ExcelProperty("标签高度(mm)")
    private Integer heightMm;

    /**
     * 网络打印机地址
     */
    @ExcelProperty("打印机地址")
    private String printerHost;

    /**
     * 网络打印机端口
     */
    @ExcelProperty("打印机端口")
    private Integer printerPort;

    /**
     * 字段映射布局 JSON
     */
    private String content;

    /**
     * 是否启用(Y/N)
     */
    @ExcelProperty("是否启用")
    private String enabled;

    /**
     * 备注
     */
    private String remark;
}
