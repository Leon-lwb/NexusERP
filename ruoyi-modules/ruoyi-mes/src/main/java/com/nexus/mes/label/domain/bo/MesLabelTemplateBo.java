package com.nexus.mes.label.domain.bo;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.label.domain.MesLabelTemplate;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签打印模板业务对象 mes_label_template
 * <p>承载 标签模板 的增改入参与查询条件。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MesLabelTemplate.class, reverseConvertGenerate = false)
public class MesLabelTemplateBo extends BaseEntity {

    /**
     * 模板ID
     */
    private Long templateId;

    /**
     * 模板编码(唯一)
     */
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;

    /**
     * 模板修订号；旧客户端可不传，服务端仍按实际修订号保护并发保存
     */
    private Long templateRevision;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /**
     * 标签类型: SN
     */
    private String labelType;

    /**
     * 打印机语言: TSPL / ZPL
     */
    @NotBlank(message = "打印机语言不能为空")
    private String labelLang;

    /**
     * 标签宽度(mm)
     */
    private Integer widthMm;

    /**
     * 标签高度(mm)
     */
    private Integer heightMm;

    /**
     * 网络打印机地址
     */
    private String printerHost;

    /**
     * 网络打印机端口(RAW 9100)
     */
    private Integer printerPort;

    /**
     * 字段映射布局 JSON
     */
    private String content;

    /**
     * 是否启用(Y/N)
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;
}
