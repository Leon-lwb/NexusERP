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

    /**
     * 回收资源项(MES-FLASH-06): CAS {@code ALLOCATED -> AVAILABLE} + 清 bound_sn_id/bound_time + 池 used_count-1。
     * <p><b>幂等语义</b>: 资源项不存在或已非 ALLOCATED(已被回收/已消耗为 USED)时, 只 {@code log.warn} 并返回 false,
     * 不抛异常 —— 回收是补偿动作, 可被重复调用(重试/并发)而不放大故障; 调用方据返回值决定是否留痕。</p>
     * <p>已消耗(USED)的资源不回收: PASS 后的资源视为已烧录, 不得回流池中再次分配。</p>
     *
     * @param itemId 资源项ID
     * @param reason 回收原因(留痕, 追加到资源项备注)
     * @return true 本次确实完成回收; false 幂等跳过(无需回收)
     */
    boolean release(Long itemId, String reason);

    /**
     * 按 SN 回写刷写结果对资源项状态(MES-FLASH-06, 池类刷写专用):
     * PASS -&gt; 该 SN 已分配的 ALLOCATED 资源置 {@code USED}(已消耗, 不可再分配);
     * FAIL -&gt; 调 {@link #release(Long, String)} 回收为 AVAILABLE。
     * <p>幂等且容错: 该 SN 在该类型池中无 ALLOCATED 资源时只告警返回 0(未分配/已回写), 不抛异常。</p>
     *
     * @param snId         SN ID
     * @param resourceType 池资源类型(对应 mes_resource_pool.resource_type, 如 MAC/SN)
     * @param pass         本次刷写是否 PASS
     * @param reason       原因(留痕)
     * @return 受影响的资源项数
     */
    int applyFlashResult(Long snId, String resourceType, boolean pass, String reason);
}
