package com.nexus.online.domain.vo;

import lombok.Data;

/**
 * 具体的字典数据视图
 *
 * @author wanghuan
 */

@Data
public class DictVo {
	/**
     * 字典项 ID（对应字典数据表中的 dict_value）。
     */
    private String id;
    /**
     * 字典项名称（对应字典数据表中的 dict_label）。
     */
    private String name;
}
