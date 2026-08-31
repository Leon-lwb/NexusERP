package com.nexus.common.flowable.core;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表单属性类
 *
 * @author wanghuan
 * @createTime 2022/8/6 18:54
 */
@Data
public class FormConf {

    /**
     * 标题
     */
    private String title;

    /**
     * 禁用表单
     */
    private Boolean disabled = false;
    /**
     * 表单按钮
     */
    private Boolean formBtns = true;

    /**
     * 表单模型
     */
    private Map<String, Object> formModel;

    /**
     * 表单数据
     */
    private Map<String, Object> formData;

	 /**
     * 表单列表
     */

    private List<Map<String, Object>> list;

    /**
     * 表单配置名
     */

    private Map<String, Object> config;

    /**
     * 表单列表实际值
     */
    private Map<String, Object> formValues;
}
