package com.nexus.demo.controller.queue;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体类 注意不允许使用内部类 否则会找不到类
 *
 * @author Lion Li
 * @version 3.6.0
 * @deprecated redisson 新版本已经将队列功能标记删除 一些技术问题无法解决 建议搭建MQ使用
 */
@Deprecated
@Data
@NoArgsConstructor
public class PriorityDemo implements Comparable<PriorityDemo> {
    /** 元素名称 */
    private String name;
    /** 排序号，数值越小优先级越高（由 compareTo 实现决定） */
    private Integer orderNum;

    @Override
    public int compareTo(PriorityDemo other) {
        return Integer.compare(getOrderNum(), other.getOrderNum());
    }
}
