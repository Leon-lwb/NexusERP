package com.nexus.common.common.enums;

import lombok.Getter;

/**
 * @author nbacheng
 * @createTime 2022/6/28 9:51
 */
@Getter
public enum FormType {
    /**
     * 流程表单
     */
    PROCESS(0),

    /**
     * 外置表单
     */
    EXTERNAL(1),

    /**
     * 节点独立表单
     */
    INDEPENDENT(2);

    /**
     * 表单类型
     */
    private final Integer type;

    FormType(Integer type) {
        this.type = type;
    }
}
