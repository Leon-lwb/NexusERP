package com.nexus.mes.ncr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.ncr.domain.NcrDefectCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * MES NCR缺陷代码视图对象 ncr_defect_code
 * <p>属于 MES NCR 不合格品管理模块，作为 缺陷代码 的查询/导出返回结构，支持树形层级。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = NcrDefectCode.class)
public class NcrDefectCodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 代码ID */
    @ExcelProperty("代码ID")
    private Long codeId;

    /** 父级ID */
    private Long parentId;

    /** 缺陷编码 */
    @ExcelProperty("缺陷编码")
    private String code;

    /** 缺陷名称 */
    @ExcelProperty("缺陷名称")
    private String name;

    /** 层级 */
    @ExcelProperty("层级")
    private Integer level;

    /** 是否启用 */
    @ExcelProperty("是否启用")
    private String enabled;

    /** 排序号 */
    @ExcelProperty("排序号")
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    // ==================== 树形子节点(非DB) ====================

    /** 子节点列表(树形结构) */
    private List<NcrDefectCodeVo> children;
}