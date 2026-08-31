package com.nexus.system.query;

import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 查询条件生成器
 * <p>提供基于查询对象与请求参数构造 MyBatis-Plus QueryWrapper 的辅助能力。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Slf4j
public class QueryGenerator {

    /**
     * 初始化查询条件的 QueryWrapper（当前为基础骨架，预留按参数动态构造逻辑）。
     *
     * @param searchObj    查询实体对象
     * @param parameterMap 请求参数映射（字段名 -> 值数组）
     * @param <T>          查询实体类型
     * @return 初始化完成的 QueryWrapper 实例
     */
    public static <T> QueryWrapper<T> initQueryWrapper(T searchObj, Map<String, String[]> parameterMap) {
        long start = System.currentTimeMillis();
        QueryWrapper<T> queryWrapper = new QueryWrapper<T>();
        log.debug("---查询条件构造器初始化完成,耗时:"+(System.currentTimeMillis()-start)+"毫秒----");
        return queryWrapper;
    }

}
