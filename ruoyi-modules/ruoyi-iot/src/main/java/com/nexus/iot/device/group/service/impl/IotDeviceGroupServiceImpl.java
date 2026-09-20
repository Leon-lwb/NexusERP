package com.nexus.iot.device.group.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import io.github.linpeilie.Converter;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.group.domain.IotDeviceGroup;
import com.nexus.iot.device.group.domain.IotDeviceGroupRel;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupAssignBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceGroupVo;
import com.nexus.iot.device.group.mapper.IotDeviceGroupMapper;
import com.nexus.iot.device.group.mapper.IotDeviceGroupRelMapper;
import com.nexus.iot.device.group.service.IIotDeviceGroupService;
import com.nexus.iot.device.group.service.IotDeviceFilterResolver;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IoT设备分组Service业务层处理。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>树形用 {@code ancestors} 物化路径（与 {@code sys_dept} 同构），子树查询/移动只需前缀重写；</li>
 *   <li>设备-分组关系在 {@link IotDeviceGroupRel}（N:N），挂载/移出均幂等；</li>
 *   <li>删除分组前校验"无子分组 + 无已挂设备"，避免产生孤儿关系。</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceGroupServiceImpl implements IIotDeviceGroupService {

    /** 根分组的父ID */
    private static final long ROOT_PARENT_ID = 0L;

    private final IotDeviceGroupMapper baseMapper;
    private final IotDeviceGroupRelMapper relMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotDeviceFilterResolver filterResolver;
    /**
     * mapstruct-plus 转换器（注入而非静态 {@code MapstructUtils}：后者的静态初始化依赖 Spring 容器，
     * 无法在单测中打桩，见本批次测试说明）。
     */
    private final Converter converter;

    /**
     * 查询分组列表（平铺）。
     *
     * @param bo 查询条件
     * @return 分组列表
     */
    @Override
    public List<IotDeviceGroupVo> selectList(IotDeviceGroupBo bo) {
        LambdaQueryWrapper<IotDeviceGroup> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getGroupNameLike()), IotDeviceGroup::getGroupName, bo.getGroupNameLike());
        lqw.like(StringUtils.isNotBlank(bo.getGroupName()) && StringUtils.isBlank(bo.getGroupNameLike()),
            IotDeviceGroup::getGroupName, bo.getGroupName());
        lqw.eq(StringUtils.isNotBlank(bo.getGroupCode()), IotDeviceGroup::getGroupCode, bo.getGroupCode());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotDeviceGroup::getStatus, bo.getStatus());
        lqw.orderByAsc(IotDeviceGroup::getParentId).orderByAsc(IotDeviceGroup::getOrderNum)
            .orderByAsc(IotDeviceGroup::getGroupId);
        List<IotDeviceGroupVo> list = baseMapper.selectVoList(lqw);
        fillDeviceCount(list);
        return list;
    }

    /**
     * 查询分组详情。
     *
     * @param groupId 分组ID
     * @return 分组VO，不存在返回 null
     */
    @Override
    public IotDeviceGroupVo selectById(Long groupId) {
        if (groupId == null) {
            return null;
        }
        IotDeviceGroupVo vo = baseMapper.selectVoById(groupId);
        if (vo != null) {
            fillDeviceCount(List.of(vo));
        }
        return vo;
    }

    /**
     * 新增分组。
     *
     * @param bo 分组业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotDeviceGroupBo bo) {
        IotDeviceGroup entity = converter.convert(bo, IotDeviceGroup.class);
        if (entity == null) {
            throw new ServiceException("分组数据转换失败");
        }
        Long parentId = entity.getParentId() == null ? ROOT_PARENT_ID : entity.getParentId();
        entity.setParentId(parentId);
        entity.setAncestors(resolveAncestors(parentId, null));
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("0");
        }
        if (entity.getOrderNum() == null) {
            entity.setOrderNum(0);
        }
        try {
            return baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("分组编码已存在：" + bo.getGroupCode());
        }
    }

    /**
     * 修改分组（父分组变更时级联重写后代 ancestors）。
     *
     * @param bo 分组业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotDeviceGroupBo bo) {
        if (bo.getGroupId() == null) {
            throw new ServiceException("分组ID不能为空");
        }
        IotDeviceGroup old = baseMapper.selectById(bo.getGroupId());
        if (old == null) {
            throw new ServiceException("分组不存在：" + bo.getGroupId());
        }
        IotDeviceGroup entity = converter.convert(bo, IotDeviceGroup.class);
        if (entity == null) {
            throw new ServiceException("分组数据转换失败");
        }
        Long newParentId = entity.getParentId() == null ? old.getParentId() : entity.getParentId();
        entity.setParentId(newParentId);
        String oldSelfPath = selfPath(old.getAncestors(), old.getGroupId());
        String newAncestors = resolveAncestors(newParentId, old.getGroupId());
        entity.setAncestors(newAncestors);
        int rows;
        try {
            rows = baseMapper.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("分组编码已存在：" + bo.getGroupCode());
        }
        // 父分组变化 → 后代 ancestors 前缀重写，保持物化路径自洽（否则子树查询会漏/串）
        String newSelfPath = selfPath(newAncestors, old.getGroupId());
        if (!oldSelfPath.equals(newSelfPath)) {
            rewriteDescendants(old.getGroupId(), oldSelfPath, newSelfPath);
        }
        return rows;
    }

    /**
     * 批量删除分组。
     *
     * @param ids 分组ID数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        for (Long id : ids) {
            Long children = baseMapper.selectCount(Wrappers.<IotDeviceGroup>lambdaQuery()
                .eq(IotDeviceGroup::getParentId, id));
            if (children != null && children > 0) {
                throw new ServiceException("分组[" + id + "]下存在子分组，请先删除子分组");
            }
            Long bound = relMapper.selectCount(Wrappers.<IotDeviceGroupRel>lambdaQuery()
                .eq(IotDeviceGroupRel::getGroupId, id));
            if (bound != null && bound > 0) {
                throw new ServiceException("分组[" + id + "]下已挂载 " + bound + " 台设备，请先移出设备");
            }
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 批量挂入/移出分组（幂等）。
     *
     * @param bo 请求体
     * @return 实际变更关系行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignDevices(IotDeviceGroupAssignBo bo) {
        if (bo.getGroupId() == null) {
            throw new ServiceException("分组ID不能为空");
        }
        IotDeviceGroup group = baseMapper.selectById(bo.getGroupId());
        if (group == null) {
            throw new ServiceException("分组不存在：" + bo.getGroupId());
        }
        List<Long> deviceIds = distinct(bo.getDeviceIds());
        requireDevicesExist(deviceIds);
        String tenantId = TenantHelper.getTenantId();
        boolean remove = Boolean.TRUE.equals(bo.getRemove());
        if (remove) {
            int rows = relMapper.delete(Wrappers.<IotDeviceGroupRel>lambdaQuery()
                .eq(IotDeviceGroupRel::getGroupId, bo.getGroupId())
                .in(IotDeviceGroupRel::getDeviceId, deviceIds));
            log.info("设备移出分组：groupId={}, deviceIds={}, rows={}", bo.getGroupId(), deviceIds, rows);
            return rows;
        }
        // 已存在的关系跳过，避免唯一键冲突（幂等）
        List<IotDeviceGroupRel> exists = relMapper.selectList(Wrappers.<IotDeviceGroupRel>lambdaQuery()
            .eq(IotDeviceGroupRel::getGroupId, bo.getGroupId())
            .in(IotDeviceGroupRel::getDeviceId, deviceIds));
        List<Long> existed = new ArrayList<>();
        for (IotDeviceGroupRel rel : exists) {
            existed.add(rel.getDeviceId());
        }
        int rows = 0;
        Date now = new Date();
        for (Long deviceId : deviceIds) {
            if (existed.contains(deviceId)) {
                continue;
            }
            IotDeviceGroupRel rel = new IotDeviceGroupRel();
            rel.setTenantId(tenantId);
            rel.setDeviceId(deviceId);
            rel.setGroupId(bo.getGroupId());
            rel.setCreateTime(now);
            rows += relMapper.insert(rel);
        }
        log.info("设备挂入分组：groupId={}, deviceIds={}, inserted={}", bo.getGroupId(), deviceIds, rows);
        return rows;
    }

    /**
     * 查询分组（含子树）下的设备ID集合。
     *
     * @param groupId         分组ID
     * @param includeSubGroup 是否包含子分组
     * @return 设备ID列表
     */
    @Override
    public List<Long> selectDeviceIds(Long groupId, Boolean includeSubGroup) {
        if (groupId == null) {
            return List.of();
        }
        List<Long> groupIds = Boolean.TRUE.equals(includeSubGroup)
            ? filterResolver.collectSelfAndDescendants(groupId)
            : List.of(groupId);
        return relMapper.selectDeviceIdsByGroupIds(TenantHelper.getTenantId(), groupIds);
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 计算 ancestors：根分组为 "0"，否则 = 父分组 ancestors + "," + 父分组ID。
     *
     * @param parentId 父分组ID
     * @param selfId   自身ID（校验不得挂到自身/后代下）
     * @return ancestors 值
     */
    private String resolveAncestors(Long parentId, Long selfId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return "0";
        }
        if (selfId != null && selfId.equals(parentId)) {
            throw new ServiceException("父分组不能是自身");
        }
        IotDeviceGroup parent = baseMapper.selectById(parentId);
        if (parent == null) {
            throw new ServiceException("父分组不存在：" + parentId);
        }
        if (selfId != null && isDescendant(parent.getAncestors(), selfId)) {
            throw new ServiceException("父分组不能是当前分组的子分组（会造成循环层级）");
        }
        String parentAncestors = StringUtils.isBlank(parent.getAncestors()) ? "0" : parent.getAncestors();
        return parentAncestors + "," + parent.getGroupId();
    }

    /**
     * 判断给定 ancestors 路径中是否包含某个分组ID。
     */
    private boolean isDescendant(String ancestors, Long groupId) {
        if (StringUtils.isBlank(ancestors) || groupId == null) {
            return false;
        }
        String target = String.valueOf(groupId);
        for (String seg : ancestors.split(",")) {
            if (target.equals(seg.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 自身物化路径 = ancestors + "," + selfId。
     */
    private String selfPath(String ancestors, Long selfId) {
        String base = StringUtils.isBlank(ancestors) ? "0" : ancestors;
        return base + "," + selfId;
    }

    /**
     * 父分组变更后重写全部后代的 ancestors 前缀。
     */
    private void rewriteDescendants(Long groupId, String oldSelfPath, String newSelfPath) {
        List<IotDeviceGroup> all = baseMapper.selectList(Wrappers.<IotDeviceGroup>lambdaQuery()
            .select(IotDeviceGroup::getGroupId, IotDeviceGroup::getAncestors));
        int updated = 0;
        for (IotDeviceGroup g : all) {
            if (g.getGroupId() == null || g.getGroupId().equals(groupId)) {
                continue;
            }
            String anc = g.getAncestors();
            // 直接子代的 ancestors 恰好等于 oldSelfPath，更深的后代以 "oldSelfPath," 为前缀
            if (StringUtils.isBlank(anc)
                || (!anc.equals(oldSelfPath) && !anc.startsWith(oldSelfPath + ","))) {
                continue;
            }
            IotDeviceGroup update = new IotDeviceGroup();
            update.setGroupId(g.getGroupId());
            update.setAncestors(newSelfPath + anc.substring(oldSelfPath.length()));
            baseMapper.updateById(update);
            updated++;
        }
        if (updated > 0) {
            log.info("分组层级变更，已重写 {} 个后代分组的 ancestors：groupId={}, {} -> {}",
                updated, groupId, oldSelfPath, newSelfPath);
        }
    }

    /**
     * 填充各分组的已挂设备数（一次 GROUP BY 查询，避免 N+1）。
     */
    private void fillDeviceCount(List<IotDeviceGroupVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (IotDeviceGroupVo vo : list) {
            if (vo.getGroupId() != null) {
                ids.add(vo.getGroupId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> countMap = new HashMap<>();
        try {
            List<Map<String, Object>> rows = relMapper.countDevicesByGroupIds(TenantHelper.getTenantId(), ids);
            for (Map<String, Object> row : rows) {
                Object gid = row.get("groupId");
                Object cnt = row.get("deviceCount");
                if (gid instanceof Number n && cnt instanceof Number c) {
                    countMap.put(n.longValue(), c.longValue());
                }
            }
        } catch (Exception e) {
            log.warn("统计分组设备数失败（不影响列表返回）：{}", e.getMessage());
        }
        for (IotDeviceGroupVo vo : list) {
            vo.setDeviceCount(countMap.getOrDefault(vo.getGroupId(), 0L));
        }
    }

    /**
     * 校验设备存在（多租户插件已按当前租户过滤，跨租户 ID 会被视为不存在）。
     */
    private void requireDevicesExist(List<Long> deviceIds) {
        if (deviceIds.isEmpty()) {
            throw new ServiceException("设备ID集合不能为空");
        }
        List<IotDevice> devices = deviceMapper.selectBatchIds(deviceIds);
        if (devices.size() != deviceIds.size()) {
            throw new ServiceException("部分设备不存在或无权限访问，请刷新后重试");
        }
    }

    private List<Long> distinct(List<Long> ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
