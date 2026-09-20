package com.nexus.iot.device.group.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.group.domain.IotDeviceGroup;
import com.nexus.iot.device.group.mapper.IotDeviceGroupMapper;
import com.nexus.iot.device.group.mapper.IotDeviceGroupRelMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagRelMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IoT 设备筛选条件解析器（分组 / 标签 → 设备ID集合）。
 * <p>
 * 供设备列表查询复用：把「按分组（可含子树）」「按标签（多标签取交集）」翻译为设备ID集合，
 * 再交由 {@code IotDeviceServiceImpl} 下推为 {@code device_id IN (...)}，避免在内存中过滤全表。
 * <p>
 * 语义约定：无任何筛选条件返回 {@code null}（表示"不限制"）；有条件但命中空集返回空列表
 * （调用方必须翻译为"无匹配"，而不是忽略条件）。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IotDeviceFilterResolver {

    private final IotDeviceGroupMapper groupMapper;
    private final IotDeviceGroupRelMapper groupRelMapper;
    private final IotDeviceTagRelMapper tagRelMapper;

    /**
     * 解析筛选条件为设备ID集合。
     *
     * @param groupId         分组ID（可空）
     * @param includeSubGroup 是否包含子分组
     * @param tagIds          标签ID集合（可空；多个标签取交集，即"同时具备这些标签"）
     * @return 设备ID列表；无筛选条件返回 null
     */
    public List<Long> resolveDeviceIds(Long groupId, Boolean includeSubGroup, List<Long> tagIds) {
        boolean hasGroup = groupId != null;
        boolean hasTag = tagIds != null && !tagIds.isEmpty();
        if (!hasGroup && !hasTag) {
            return null;
        }
        String tenantId = TenantHelper.getTenantId();
        Set<Long> result = null;
        if (hasGroup) {
            List<Long> groupIds = Boolean.TRUE.equals(includeSubGroup)
                ? collectSelfAndDescendants(groupId)
                : List.of(groupId);
            result = new HashSet<>(groupRelMapper.selectDeviceIdsByGroupIds(tenantId, groupIds));
        }
        if (hasTag) {
            Set<Long> byTag = new HashSet<>(tagRelMapper.selectDeviceIdsByTagIds(tenantId, tagIds));
            if (result == null) {
                result = byTag;
            } else {
                // 分组 + 标签：取交集（既要属于该分组，又要具备全部标签）
                result.retainAll(byTag);
            }
        }
        return result == null ? List.of() : new ArrayList<>(result);
    }

    /**
     * 收集分组自身 + 全部后代分组ID。
     * <p>
     * 按 {@code ancestors} 物化路径一次性查询该租户全部分组后在内存中筛选：
     * 分组数量为配置类数据（通常数十~数百），避免递归 SQL / 多次往返。
     *
     * @param groupId 分组ID
     * @return 分组ID列表（含自身）
     */
    public List<Long> collectSelfAndDescendants(Long groupId) {
        if (groupId == null) {
            return List.of();
        }
        LambdaQueryWrapper<IotDeviceGroup> lqw = Wrappers.<IotDeviceGroup>lambdaQuery()
            .select(IotDeviceGroup::getGroupId, IotDeviceGroup::getParentId, IotDeviceGroup::getAncestors);
        List<IotDeviceGroup> all = groupMapper.selectList(lqw);
        List<Long> ids = new ArrayList<>();
        ids.add(groupId);
        String target = String.valueOf(groupId);
        for (IotDeviceGroup g : all) {
            if (g.getGroupId() == null || g.getGroupId().equals(groupId)
                || StringUtils.isBlank(g.getAncestors())) {
                continue;
            }
            for (String seg : g.getAncestors().split(",")) {
                if (target.equals(seg.trim())) {
                    ids.add(g.getGroupId());
                    break;
                }
            }
        }
        return ids;
    }
}
