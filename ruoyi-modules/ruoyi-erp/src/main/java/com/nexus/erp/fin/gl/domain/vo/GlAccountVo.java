package com.nexus.erp.fin.gl.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.gl.domain.GlAccount;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会计科目视图对象 gl_account
 *
 * @author nexus
 * @date 2026-07-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = GlAccount.class)
public class GlAccountVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "科目ID")
    private Long accountId;

    @ExcelProperty(value = "科目编码")
    private String accountCode;

    @ExcelProperty(value = "科目名称")
    private String accountName;

    @ExcelProperty(value = "科目类型")
    private String accountType;

    @ExcelProperty(value = "余额方向")
    private String balanceDir;

    /** 父科目ID(0=顶级) */
    private Long parentId;

    /** 层级(1=一级) */
    private Integer levelNum;

    /** 路径ID串 */
    private String pathIds;

    /** 路径名称串 */
    private String pathNames;

    /** 是否末级 */
    private String isLeaf;

    /** 是否统驭科目 */
    private String isRecon;

    /** 统驭类型 */
    private String reconType;

    /** 是否未清项管理 */
    private String isOpenItem;

    /** 币种 */
    private String currency;

    /** 状态 */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    private Integer version;
}
