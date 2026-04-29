package org.dromara.online.domain.vo;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import org.dromara.generator.domain.GenTable;
import org.dromara.generator.domain.GenTableColumn;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * 在线表单视图
 *
 * @author nbacheng
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
