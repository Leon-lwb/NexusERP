package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProSn;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES SN序列号视图对象 pro_sn
 * <p>属于 MES 生产管理模块，作为 SN序列号追溯 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProSn.class)
public class ProSnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SN ID */
    @ExcelProperty("SN ID")
    private Long snId;

    /** SN序列号编码 */
    @ExcelProperty("SN序列号")
    private String snCode;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号 */
    @ExcelProperty("流转卡编号")
    private String cardNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 当前工序序号 */
    @ExcelProperty("当前工序序号")
    private Integer currentStepSeq;

    /** SN状态 */
    @ExcelProperty("状态")
    private String snStatus;

    /** 父SN ID */
    private Long parentSnId;

    /** 父SN编码 */
    @ExcelProperty("父SN编码")
    private String parentSnCode;

    /** 来源 */
    @ExcelProperty("来源")
    private String source;

    /** 来源单据ID(委外=subcontract_id) */
    private Long sourceId;

    /** 委外单号(冗余,SN直达追溯委外订单) */
    @ExcelProperty("委外单号")
    private String subcontractNo;

    /** 生产日期 */
    @ExcelProperty("生产日期")
    private Date productionDate;

    /** 备注 */
    private String remark;

    // ==================== 追溯辅助字段(非DB, 仅trace使用) ====================

    /** 子SN列表(前向追溯: 当前SN作为父SN的下级) */
    private List<ProSnVo> children;

    /** 前向追溯的工序链(来自 pro_flow_card_step) */
    private List<Map<String, Object>> steps;
}
