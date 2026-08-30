package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.vo.pda.PdaBasedataSyncVo;

/**
 * PDA 主数据增量同步服务 (物料/库位/仓库)
 */
public interface IPdaBasedataService {

    /**
     * @param lastSyncTime 客户端上次同步游标(服务端时间戳 ms), 0 表示首次全量
     */
    PdaBasedataSyncVo sync(Long lastSyncTime);
}
