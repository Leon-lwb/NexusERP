package com.nexus.online.domain.vo;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.nexus.generator.domain.GenTable;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * 在线表单视图
 *
 * @author wanghuan
 */

@Data
public class TableVo extends GenTable {
	/**
     * 在线表单字段视图列表（非数据库字段，承载表单列配置）。
     */
    @Valid
    @TableField(exist = false)
    private List<OnlFieldVo> columnsVo;
}
