package com.nexus.mes.flash.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.domain.bo.MesResourcePoolBo;

/**
 * 刷写资源池 Service
 *
 * @author Nexus
 */
public interface IMesResourcePoolService {

    TableDataInfo<MesResourcePool> selectPageList(MesResourcePoolBo bo, PageQuery pageQuery);

    MesResourcePool selectById(Long poolId);

    Boolean insertByBo(MesResourcePoolBo bo);

    Boolean updateByBo(MesResourcePoolBo bo);

    Boolean deleteByIds(Long[] ids);

    /**
     * 批量导入资源项(文本按换行/逗号分隔, 池内去重)
     *
     * @return 导入条数
     */
    int importItems(Long poolId, String text);

    /**
     * 原子分配一可用资源给SN(CAS + 重试, 一SN绑一资源)
     *
     * @return 已分配(ALLOCATED)的资源项
     */
    MesResourceItem allocate(Long poolId, Long snId, String snCode);
}
