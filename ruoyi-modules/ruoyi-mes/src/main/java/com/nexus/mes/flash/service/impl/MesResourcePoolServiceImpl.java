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
}
