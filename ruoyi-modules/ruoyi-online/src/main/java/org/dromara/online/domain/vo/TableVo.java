package org.dromara.online.domain.vo;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import org.dromara.generator.domain.GenTable;

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
     * 表列信息
     */
    @Valid
    @TableField(exist = false)
    private List<OnlFieldVo> columnsVo;
}
