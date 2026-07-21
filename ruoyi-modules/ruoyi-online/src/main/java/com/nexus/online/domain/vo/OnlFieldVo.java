package com.nexus.online.domain.vo;

import java.util.List;

import com.nexus.generator.domain.GenTableColumn;

import lombok.Data;

/**
 * 在线表单字段视图
 *
 * @author wanghuan
 */

@Data
public class OnlFieldVo extends GenTableColumn {
	/** 序列化版本号。 */
	private static final long serialVersionUID = 1L;
	/** 字段对应的字典数据列表（由 dictType 解析得到）。 */
	private List<DictVo> commonDictCode;
}
