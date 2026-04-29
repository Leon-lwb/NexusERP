package org.dromara.online.domain.vo;

import java.util.List;

import org.dromara.generator.domain.GenTableColumn;

import lombok.Data;

/**
 * 在线表单字段视图
 *
 * @author nbacheng
 */

@Data
public class OnlFieldVo extends GenTableColumn {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 返回字段的字典数据
	 */
	private List<DictVo> commonDictCode;
}
