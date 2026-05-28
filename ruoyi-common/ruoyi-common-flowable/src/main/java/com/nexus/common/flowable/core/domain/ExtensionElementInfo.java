package com.nexus.common.flowable.core.domain;

import java.io.Serializable;

import lombok.Data;

@Data
public class ExtensionElementInfo implements Serializable {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/**
     * 名称
     */
    private String name;

    /**
     * 值
     */
    private String value;

    /**
     * 表达式
     */
    private String expression;
}
