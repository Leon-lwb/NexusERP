package com.nexus.core.wm.service;

import com.nexus.common.core.domain.AjaxResult;

/**
 * 库存核心Service接口
 *
 * @author nexus
 * @date 2025-12-11
 */
public interface IWmInventoryCoreService {

    /**
     * 执行物料入库
     * @param receiveId 入库单ID
     * @return 结果
     */
    public AjaxResult executeReceive(Long receiveId);
}