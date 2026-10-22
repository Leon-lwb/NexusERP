package com.nexus.mes.flash.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.domain.bo.MesResourcePoolBo;
import com.nexus.mes.flash.mapper.MesResourceItemMapper;
import com.nexus.mes.flash.mapper.MesResourcePoolMapper;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import com.nexus.system.strategy.AutoCodeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 刷写资源池 Service 实现
 * <p>资源池 CRUD + 资源项批量导入 + 原子分配(CAS + 重试, 一SN绑一资源)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class MesResourcePoolServiceImpl implements IMesResourcePoolService {

    private final MesResourcePoolMapper baseMapper;
    private final MesResourceItemMapper resourceItemMapper;
    private final AutoCodeUtil autoCodeUtil;

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_ALLOCATED = "ALLOCATED";
    /** 已消耗: 刷写 PASS 后资源被烧录使用, 不可再分配/不再回收 */
    private static final String STATUS_USED = "USED";
    /** 备注最大长度(与 mes_resource_item.remark 列对齐) */
    private static final int REMARK_MAX_LEN = 200;

    @Override
    public TableDataInfo<MesResourcePool> selectPageList(MesResourcePoolBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<MesResourcePool> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getPoolName()), MesResourcePool::getPoolName, bo.getPoolName())
            .eq(StringUtils.isNotBlank(bo.getResourceType()), MesResourcePool::getResourceType, bo.getResourceType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), MesResourcePool::getStatus, bo.getStatus())
            .orderByDesc(MesResourcePool::getCreateTime);
        Page<MesResourcePool> page = baseMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public MesResourcePool selectById(Long poolId) {
        return baseMapper.selectById(poolId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(MesResourcePoolBo bo) {
        MesResourcePool pool = new MesResourcePool();
        pool.setPoolCode(autoCodeUtil.genSerialCode("MES_POOL_CODE", null));
        pool.setPoolName(bo.getPoolName());
        pool.setResourceType(bo.getResourceType());
        pool.setSource(StringUtils.isBlank(bo.getSource()) ? "INTERNAL" : bo.getSource());
        pool.setExternalConfig(bo.getExternalConfig());
        pool.setAllocStrategy(StringUtils.isBlank(bo.getAllocStrategy()) ? "FIFO" : bo.getAllocStrategy());
        pool.setUsedCount(0);
        pool.setStatus(StringUtils.isBlank(bo.getStatus()) ? "ENABLED" : bo.getStatus());
        pool.setRemark(bo.getRemark());
        baseMapper.insert(pool);
        log.info("资源池新增: poolId={}, code={}, type={}", pool.getPoolId(), pool.getPoolCode(), pool.getResourceType());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(MesResourcePoolBo bo) {
        MesResourcePool pool = baseMapper.selectById(bo.getPoolId());
        if (pool == null) {
            throw new ServiceException("资源池不存在: " + bo.getPoolId());
        }
        pool.setPoolName(bo.getPoolName());
        pool.setResourceType(bo.getResourceType());
        pool.setSource(bo.getSource());
        pool.setExternalConfig(bo.getExternalConfig());
        pool.setAllocStrategy(bo.getAllocStrategy());
        pool.setStatus(bo.getStatus());
        pool.setRemark(bo.getRemark());
        baseMapper.updateById(pool);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        // 校验: 含可用/已分配资源的池不可删
        for (Long id : ids) {
            Long active = resourceItemMapper.selectCount(Wrappers.<MesResourceItem>lambdaQuery()
                .eq(MesResourceItem::getPoolId, id)
                .in(MesResourceItem::getStatus, STATUS_AVAILABLE, STATUS_ALLOCATED));
            if (active != null && active > 0) {
                throw new ServiceException("资源池含可用/已分配资源, 不可删除: poolId=" + id);
            }
        }
        baseMapper.deleteByIds(Arrays.asList(ids));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importItems(Long poolId, String text) {
        if (baseMapper.selectById(poolId) == null) {
            throw new ServiceException("资源池不存在: " + poolId);
        }
        if (StringUtils.isBlank(text)) {
            throw new ServiceException("导入内容不能为空");
        }
        // 换行/逗号分隔
        List<String> values = Arrays.stream(text.split("[\n\r,;]+"))
            .map(String::trim).filter(StringUtils::isNotBlank).distinct().toList();
        int count = 0;
        for (String val : values) {
            // 池内去重
            Long exist = resourceItemMapper.selectCount(Wrappers.<MesResourceItem>lambdaQuery()
                .eq(MesResourceItem::getPoolId, poolId)
                .eq(MesResourceItem::getResourceValue, val));
            if (exist != null && exist > 0) {
                continue;
            }
            MesResourceItem item = new MesResourceItem();
            item.setPoolId(poolId);
            item.setResourceValue(val);
            item.setStatus(STATUS_AVAILABLE);
            resourceItemMapper.insert(item);
            count++;
        }
        // 回填总数
        Long total = resourceItemMapper.selectCount(Wrappers.<MesResourceItem>lambdaQuery()
            .eq(MesResourceItem::getPoolId, poolId));
        baseMapper.update(null, Wrappers.<MesResourcePool>lambdaUpdate()
            .set(MesResourcePool::getTotalCount, total == null ? 0 : total.intValue())
            .eq(MesResourcePool::getPoolId, poolId));
        log.info("资源池导入: poolId={}, 导入{}条, 总{}条", poolId, count, total);
        return count;
    }

    /**
     * 原子分配: 查一个 AVAILABLE + CAS( AVAILABLE→ALLOCATED + bound_sn_id ), 失败重试3次(并发被抢)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesResourceItem allocate(Long poolId, Long snId, String snCode) {
        // 同SN已分配过则直接返回(幂等)
        MesResourceItem bound = resourceItemMapper.selectOne(Wrappers.<MesResourceItem>lambdaQuery()
            .eq(MesResourceItem::getPoolId, poolId)
            .eq(MesResourceItem::getBoundSnId, snId)
            .in(MesResourceItem::getStatus, STATUS_ALLOCATED, "USED")
            .last("LIMIT 1"));
        if (bound != null) {
            return bound;
        }
        for (int i = 0; i < 3; i++) {
            MesResourceItem item = resourceItemMapper.selectOne(Wrappers.<MesResourceItem>lambdaQuery()
                .eq(MesResourceItem::getPoolId, poolId)
                .eq(MesResourceItem::getStatus, STATUS_AVAILABLE)
                .last("LIMIT 1"));
            if (item == null) {
                throw new ServiceException("资源池无可用资源: poolId=" + poolId);
            }
            int rows = resourceItemMapper.update(null, Wrappers.<MesResourceItem>lambdaUpdate()
                .set(MesResourceItem::getStatus, STATUS_ALLOCATED)
                .set(MesResourceItem::getBoundSnId, snId)
                .set(MesResourceItem::getBoundTime, new Date())
                .eq(MesResourceItem::getItemId, item.getItemId())
                .eq(MesResourceItem::getStatus, STATUS_AVAILABLE));
            if (rows > 0) {
                baseMapper.update(null, Wrappers.<MesResourcePool>lambdaUpdate()
                    .setSql("used_count = used_count + 1")
                    .eq(MesResourcePool::getPoolId, poolId));
                log.info("资源分配: poolId={}, itemId={}, value={}, snId={}", poolId, item.getItemId(), item.getResourceValue(), snId);
                return item;
            }
            // 并发被抢, 重试
        }
        throw new ServiceException("资源分配并发冲突, 重试失败: poolId=" + poolId);
    }

    /**
     * 回收资源项(MES-FLASH-06): CAS ALLOCATED -> AVAILABLE + 清绑定 + 池 used_count-1。
     * <p>幂等: 不存在/已非 ALLOCATED 时仅告警返回 false(不抛异常, 补偿动作可重复执行)。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(Long itemId, String reason) {
        if (itemId == null) {
            return false;
        }
        MesResourceItem item = resourceItemMapper.selectById(itemId);
        if (item == null) {
            log.warn("资源回收跳过(资源项不存在, 幂等): itemId={}, reason={}", itemId, reason);
            return false;
        }
        // CAS: 仅 ALLOCATED 可回收; USED(已消耗/已烧录)不回流池中, 防止"烧过的MAC再发给下一台"
        int rows = resourceItemMapper.update(null, Wrappers.<MesResourceItem>lambdaUpdate()
            .set(MesResourceItem::getStatus, STATUS_AVAILABLE)
            .set(MesResourceItem::getBoundSnId, null)
            .set(MesResourceItem::getBoundTime, null)
            .set(StringUtils.isNotBlank(reason), MesResourceItem::getRemark, mergeRemark(item.getRemark(), reason))
            .eq(MesResourceItem::getItemId, itemId)
            .eq(MesResourceItem::getStatus, STATUS_ALLOCATED));
        if (rows == 0) {
            log.warn("资源回收跳过(非 ALLOCATED 状态, 幂等): itemId={}, status={}, reason={}", itemId, item.getStatus(), reason);
            return false;
        }
        if (item.getPoolId() != null) {
            // used_count 递减(下限 0, 防止历史脏数据出现负数)
            baseMapper.update(null, Wrappers.<MesResourcePool>lambdaUpdate()
                .setSql("used_count = CASE WHEN used_count > 0 THEN used_count - 1 ELSE 0 END")
                .eq(MesResourcePool::getPoolId, item.getPoolId()));
        }
        log.info("资源回收: itemId={}, poolId={}, value={}, reason={}", itemId, item.getPoolId(), item.getResourceValue(), reason);
        return true;
    }

    /**
     * 按 SN 回写刷写结果对资源项状态(MES-FLASH-06): PASS 置 USED(消耗) / FAIL 回收 AVAILABLE。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int applyFlashResult(Long snId, String resourceType, boolean pass, String reason) {
        if (snId == null || StringUtils.isBlank(resourceType)) {
            return 0;
        }
        List<Long> poolIds =
                baseMapper
                        .selectList(
                                Wrappers.<MesResourcePool>lambdaQuery()
                                        .eq(MesResourcePool::getResourceType, resourceType))
                        .stream()
                        .map(MesResourcePool::getPoolId)
                        .filter(Objects::nonNull)
                        .toList();
        if (poolIds.isEmpty()) {
            log.warn("刷写结果回写资源项跳过(无该类型资源池): snId={}, resourceType={}", snId, resourceType);
            return 0;
        }
        List<MesResourceItem> bound = resourceItemMapper.selectList(Wrappers.<MesResourceItem>lambdaQuery()
            .in(MesResourceItem::getPoolId, poolIds)
            .eq(MesResourceItem::getBoundSnId, snId)
            .eq(MesResourceItem::getStatus, STATUS_ALLOCATED));
        if (bound.isEmpty()) {
            log.warn("刷写结果回写资源项跳过(该SN无已分配资源, 幂等): snId={}, resourceType={}", snId, resourceType);
            return 0;
        }
        int affected = 0;
        for (MesResourceItem item : bound) {
            if (pass) {
                // PASS: 资源已烧录使用, 置 USED(使 USED 成为可达状态, 同时保留 allocate 的 in(ALLOCATED,USED) 幂等守卫)
                int rows = resourceItemMapper.update(null, Wrappers.<MesResourceItem>lambdaUpdate()
                    .set(MesResourceItem::getStatus, STATUS_USED)
                    .eq(MesResourceItem::getItemId, item.getItemId())
                    .eq(MesResourceItem::getStatus, STATUS_ALLOCATED));
                affected += Math.max(rows, 0);
            } else if (release(item.getItemId(), reason)) {
                affected++;
            }
        }
        log.info("刷写结果回写资源项: snId={}, resourceType={}, pass={}, 影响={}", snId, resourceType, pass, affected);
        return affected;
    }

    /** 回收原因并入资源项备注(保留原备注, 截断到列长) */
    private String mergeRemark(String origin, String reason) {
        String suffix = "回收:" + reason;
        String merged = StringUtils.isBlank(origin) ? suffix : origin + " | " + suffix;
        return merged.length() > REMARK_MAX_LEN ? merged.substring(0, REMARK_MAX_LEN) : merged;
    }
}
