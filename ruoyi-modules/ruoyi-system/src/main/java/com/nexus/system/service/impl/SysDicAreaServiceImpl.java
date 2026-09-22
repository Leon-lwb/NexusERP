package com.nexus.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import com.nexus.system.service.ISysDicAreaService;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行政区划 服务实现类，实现 {@link ISysDicAreaService} 接口。属于 System 系统管理模块。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDicAreaServiceImpl implements ISysDicAreaService {

    /** SysDicAreaMapper 数据访问层组件，提供 行政区划 的持久化能力。 */
    private final SysDicAreaMapper baseMapper;

    /**
     * 根据条件查询行政区划列表。
     * @param pkid 主键 ID
     * @return 行政区划对象
     */
    @Override
    public SysDicAreaVo selectSysDicAreaByPkid(Long pkid) {
        return baseMapper.selectVoById(pkid);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectSysDicAreaList(SysDicArea sysDicArea) {
        LambdaQueryWrapper<SysDicArea> wrapper = buildQueryWrapper(sysDicArea);
        wrapper.orderByAsc(SysDicArea::getSort);
        return baseMapper.selectVoList(wrapper);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        if (sysDicArea.getParentAreaId() != null) {
            return areas;
        }
        return buildTree(areas);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        return buildTree(areas);
    }

    /**
     * 根据条件查询行政区划列表。
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectProvinces() {
        List<SysDicArea> provinces = baseMapper.selectProvinces();
        return MapstructUtils.convert(provinces, SysDicAreaVo.class);
    }

    /**
     * 根据条件查询行政区划列表。
     * @param parentAreaId 上级区域 ID
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectNextLevel(Long parentAreaId) {
        List<SysDicArea> nextLevel = baseMapper.selectNextLevel(parentAreaId);
        return MapstructUtils.convert(nextLevel, SysDicAreaVo.class);
    }

    /**
     * 查询行政区划树，并排除指定节点及其整棵子树。
     * <p>按 {@code areaId}（行政区划代码）匹配，而非主键 {@code pkid}：前端"选择上级"传的是
     * {@code row.areaId}。被排除节点的直接子节点因父不在结果集中，会由 {@link #buildTree} 升级为根，
     * 因此排除的是"整棵子树"而非仅该节点本身。</p>
     *
     * @param areaId 待排除节点的行政区划代码，可为空（为空则返回全树）
     * @return 排除后的行政区划树
     */
    @Override
    public List<SysDicAreaVo> selectTreeExcludeSubtree(Long areaId) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(new SysDicArea());
        if (areaId == null) {
            return buildTree(areas);
        }
        // 匹配不到时静默返回全树会让"防环"失效，且与"传了 pkid"这一历史错误难以区分，故留痕
        if (!areas.isEmpty() && areas.stream().noneMatch(a -> areaId.equals(a.getAreaId()))) {
            log.warn("行政区划排除子树：areaId={} 不存在于 sys_dic_area，将返回全树；"
                + "若此处传入的是 pkid 而非 areaId，排除逻辑会静默失效", areaId);
        }
        Set<Long> excluded = collectSubtreeIds(areaId, areas);
        List<SysDicAreaVo> remaining = areas.stream()
            .filter(a -> a.getAreaId() == null || !excluded.contains(a.getAreaId()))
            .collect(Collectors.toList());
        return buildTree(remaining);
    }

    /**
     * 新增行政区划。
     * @param sysDicArea 待新增的行政区划
     * @return 受影响行数
     */
    @Override
    public int insertSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.insert(sysDicArea);
    }

    /**
     * 更新行政区划。
     * @param sysDicArea 待更新的行政区划
     * @return 受影响行数
     */
    @Override
    public int updateSysDicArea(SysDicArea sysDicArea) {
        return baseMapper.updateById(sysDicArea);
    }

    /**
     * 删除记录。
     * @param pkid 待删除的 ID
     * @return 受影响行数
     */
    @Override
    public int deleteSysDicAreaByPkid(Long pkid) {
        return baseMapper.deleteById(pkid);
    }

    /**
     * 删除记录。
     * @param pkids 待删除的 ID 数组
     * @return 受影响行数
     */
    @Override
    public int deleteSysDicAreaByPkids(Long[] pkids) {
        int count = 0;
        for (Long pkid : pkids) {
            count += baseMapper.deleteById(pkid);
        }
        return count;
    }

    private LambdaQueryWrapper<SysDicArea> buildQueryWrapper(SysDicArea area) {
        LambdaQueryWrapper<SysDicArea> wrapper = new LambdaQueryWrapper<>();
        if (area.getAreaId() != null) {
            wrapper.eq(SysDicArea::getAreaId, area.getAreaId());
        }
        if (area.getParentAreaId() != null) {
            wrapper.eq(SysDicArea::getParentAreaId, area.getParentAreaId());
        }
        if (area.getAreaName() != null && !area.getAreaName().isEmpty()) {
            wrapper.like(SysDicArea::getAreaName, area.getAreaName());
        }
        if (area.getLayered() != null) {
            wrapper.eq(SysDicArea::getLayered, area.getLayered());
        }
        return wrapper;
    }

    /**
     * 由扁平列表构建森林。
     * <p>根 = 父节点不在结果集中的节点（而非"层级为 1"），因此：
     * 过滤条件命中任意层级时都不会丢数据；父节点缺失的孤儿节点会升级为根，不会不可见。</p>
     *
     * <p><b>复杂度</b>：{@code area_id} 唯一时为 O(n)（BFS 每条子边只处理一次）。
     * 若存在重复 {@code area_id}，同一子列表会被每个同码节点各扫一次，
     * 退化为 O(重复数 × 扇出)；建表脚本只有非唯一索引 {@code idx_area_id}，未强制唯一。</p>
     *
     * <p><b>成环防护</b>：长度 ≥ 2 的父子环（A.parent=B, B.parent=A）会使环上节点从任何根都不可达。
     * 若只按"父不在集合中"判根，环上节点既不会出现在结果里（整棵树可能返回空），
     * 也可能让 Jackson 序列化时无限递归。故此处按 BFS 逐层挂载，只挂载"父节点已落位"的节点；
     * 遍历结束后仍未落位的节点必然在环上或环的下游，统一提升为根。</p>
     *
     * <p><b>副作用</b>：本方法会就地重置入参元素的 {@code children}。调用方必须保证传入的是独占列表，
     * 且不得复用已构建的树（否则前一次构建结果会被后续构建破坏）。</p>
     *
     * <p><b>降级语义</b>：成环时，环及其下游节点会被提升为根并丢失原有的父子关系（节点本身不丢）。
     * 这是脏数据的降级路径，正常数据不会触发；触发时会打 WARN 日志。</p>
     */
    private List<SysDicAreaVo> buildTree(List<SysDicAreaVo> areas) {
        if (CollUtil.isEmpty(areas)) {
            return new ArrayList<>();
        }
        Map<Long, SysDicAreaVo> byAreaId = new LinkedHashMap<>(areas.size());
        for (SysDicAreaVo area : areas) {
            area.setChildren(new ArrayList<>());
            if (area.getAreaId() != null) {
                byAreaId.put(area.getAreaId(), area);
            }
        }
        // 反向索引：parentAreaId -> 直接子节点（按 areas 顺序，保持 sort 排序）
        Map<Long, List<SysDicAreaVo>> childrenByParent = new HashMap<>();
        for (SysDicAreaVo area : areas) {
            if (area.getParentAreaId() != null) {
                childrenByParent.computeIfAbsent(area.getParentAreaId(), k -> new ArrayList<>()).add(area);
            }
        }
        // 根：父节点缺失，或脏数据把自己当父节点
        List<SysDicAreaVo> roots = new ArrayList<>();
        // 必须用 identity 集合：SysDicAreaVo 是 Lombok @Data，equals/hashCode 会递归比较 children，
        // 在成环的结构上调用会栈溢出。全程不得对节点调用 equals/hashCode。
        Set<SysDicAreaVo> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SysDicAreaVo area : areas) {
            SysDicAreaVo parent = area.getParentAreaId() == null ? null : byAreaId.get(area.getParentAreaId());
            // placed.add 兼作去重：同一实例在入参中重复出现时只登记一次，避免响应里出现重复子树
            if ((parent == null || parent == area) && placed.add(area)) {
                roots.add(area);
            }
        }
        // BFS 逐层挂载：只挂"父已落位"的节点，天然不会形成环
        Deque<SysDicAreaVo> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            SysDicAreaVo node = queue.poll();
            List<SysDicAreaVo> kids = node.getAreaId() == null ? null : childrenByParent.get(node.getAreaId());
            if (kids == null) {
                continue;
            }
            for (SysDicAreaVo kid : kids) {
                if (placed.add(kid)) {
                    node.getChildren().add(kid);
                    queue.add(kid);
                }
            }
        }
        // 仍未落位的节点：在环上或环的下游 → 提升为根，保证一个都不丢
        int promoted = 0;
        StringBuilder sample = new StringBuilder();
        for (SysDicAreaVo area : areas) {
            if (placed.add(area)) {
                roots.add(area);
                promoted++;
                // 附带有界的 areaId 样本，便于运维直接定位脏数据（最多 10 个）
                if (promoted <= 10) {
                    sample.append(sample.isEmpty() ? "" : ",").append(area.getAreaId());
                }
            }
        }
        if (promoted > 0) {
            log.warn("行政区划树构建：{} 个节点因父子成环（或挂在环下）被提升为根，areaId 样本[{}]，请检查 sys_dic_area 的 parent_area_id 数据",
                promoted, sample);
        }
        return roots;
    }

    /**
     * 收集 areaId 及其所有下级的 areaId。
     * <p>先建 {@code parentAreaId -> 子节点} 反向索引再迭代下推，复杂度 O(n)，
     * 与 {@link #buildTree} 的建树方式保持一致；不使用递归，深度不受限。
     * {@code visited} 兼作结果集与已访问集合，脏数据成环时不会死循环。</p>
     *
     * @param areaId 起始节点的行政区划代码
     * @param areas  全量扁平列表
     * @return 含起始节点及其全部下级的 areaId 集合
     */
    private Set<Long> collectSubtreeIds(Long areaId, List<SysDicAreaVo> areas) {
        Set<Long> visited = new HashSet<>();
        if (areaId == null || !visited.add(areaId)) {
            return visited;
        }
        Map<Long, List<Long>> childIdsByParent = new HashMap<>();
        for (SysDicAreaVo area : areas) {
            if (area.getParentAreaId() != null && area.getAreaId() != null) {
                childIdsByParent.computeIfAbsent(area.getParentAreaId(), k -> new ArrayList<>())
                    .add(area.getAreaId());
            }
        }
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(areaId);
        while (!stack.isEmpty()) {
            for (Long childId : childIdsByParent.getOrDefault(stack.pop(), List.of())) {
                if (visited.add(childId)) {
                    stack.push(childId);
                }
            }
        }
        return visited;
    }
}
