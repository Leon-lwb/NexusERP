package com.nexus.mes.label.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标签打印模板对象 mes_label_template
 * <p>字段映射式模板(v1): content JSON 描述 {items:[{type,field,x,y,size,height}]}
 * 逐项布局, 字段域取 SN 维度六字段(snCode/materialCode/materialName/woNo/cardNo/productionDate);
 * 由 {@code LabelRenderService} 服务端渲染为 TSPL/ZPL 指令串,
 * {@code LabelPrintService} 经 TCP 9100 直发网络打印机。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_label_template")
public class MesLabelTemplate extends TenantEntity {

    /**
     * 模板ID(主键)
     */
    @TableId(value = "template_id")
    private Long templateId;

    /**
     * 模板编码(唯一)
     */
    private String templateCode;

    /**
     * 模板修订号，新增为 1，每次保存递增，打印时冻结
     */
    private Long templateRevision;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 标签类型: SN 标签(v1 仅 SN, 流转卡标签后续扩展)
     */
    private String labelType;

    /**
     * 打印机语言: TSPL / ZPL
     */
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
     * 字段映射布局 JSON: {items:[{type:text|barcode|qrcode, field, x, y, size, height}]}
     */
    private String content;

    /**
     * 是否启用(Y/N): SN 标签打印仅消费启用模板
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic private String delFlag;
}
