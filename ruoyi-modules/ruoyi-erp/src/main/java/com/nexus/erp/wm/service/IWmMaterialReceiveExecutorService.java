package com.nexus.erp.wm.service;

/**
 * 物料入库执行Service接口
 *
 * @author nexus
 */
public interface IWmMaterialReceiveExecutorService {

    /**
     * 执行物料入库
     *
     * @param receiveId 入库单ID
     * @return 是否成功
     */
    boolean executeReceive(Long receiveId);
}
