package com.nexus.system.domain;

import com.nexus.common.core.utils.StringUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存信息
 *
 * @author wanghuan
 */
@Data
@NoArgsConstructor
public class SysCache {

    /**
     * 缓存名称
     */
    private String cacheName = "";

    /**
     * 缓存键名
     */
    private String cacheKey = "";

    /**
     * 缓存内容
     */
    private String cacheValue = "";

    /**
     * 备注
     */
    private String remark = "";

    /**
     * 构造仅含缓存名称与备注的缓存信息（键名与内容默认为空）。
     *
     * @param cacheName 缓存名称
     * @param remark 备注
     */
    public SysCache(String cacheName, String remark) {
        this.cacheName = cacheName;
        this.remark = remark;
    }

    /**
     * 构造缓存信息，并自动去除缓存名称中的冒号、剔除键名中的缓存名称前缀。
     *
     * @param cacheName 缓存名称（冒号会被去除）
     * @param cacheKey 缓存键名（会剔除缓存名称前缀）
     * @param cacheValue 缓存内容
     */
    public SysCache(String cacheName, String cacheKey, String cacheValue) {
        this.cacheName = StringUtils.replace(cacheName, ":", "");
        this.cacheKey = StringUtils.replace(cacheKey, cacheName, "");
        this.cacheValue = cacheValue;
    }

}
