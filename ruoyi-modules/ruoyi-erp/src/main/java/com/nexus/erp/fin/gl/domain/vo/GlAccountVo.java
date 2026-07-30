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

    private Long parentId;

    private Integer levelNum;

    private String pathIds;

    private String pathNames;

    private String isLeaf;

    private String isRecon;

    private String reconType;

    private String isOpenItem;

    private String currency;

    private String status;

    private Integer sortOrder;

    private Integer version;
}
