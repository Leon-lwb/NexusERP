package com.nexus.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.constant.CacheNames;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.bo.SysDicAreaBo;
import com.nexus.system.domain.vo.SysDicAreaVo;
import com.nexus.system.mapper.SysDicAreaMapper;
import com.nexus.system.service.ISysDicAreaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行政区划 服务实现类，实现 {@link ISysDicAreaService} 接口。属于 System 系统管理模块。
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysDicAreaServiceImpl implements ISysDicAreaService {

    /**
     * 全量树缓存 key（单键缓存整棵树）。
     * <p><b>为什么用编程式缓存而不是 {@code @Cacheable}</b>：{@link #selectFullTree()} 会被本类的
     * {@link #selectSysDicAreaTreeList(SysDicArea)} 直接调用，属于<b>同类自调用</b>，不经过 Spring AOP
     * 代理，注解会静默失效（与仓库 AGENTS.md 里 {@code @DS} 自调用的坑同源）。编程式缓存没有代理问题，
     * 也是本项目既有写法（{@code PrdShortageRiskServiceImpl} / {@code ImConversationCache}）。</p>
     * <p><b>租户隔离</b>：Redisson 装配的是 {@code TenantKeyPrefixHandler}，会自动给 key 加
     * {@code 租户id:} 前缀，因此单键不会跨租户串数据（与 {@link CacheNames#BD_CATEGORY_TREE} 同理）。</p>
     */
    private static final String FULL_TREE_CACHE_KEY = CacheNames.SYS_DIC_AREA_TREE;

    /**
     * 全量树缓存有效期：兜底自愈。
     * <p>写路径（增/改/删）都会主动清除，此处 TTL 仅防御两类场景：
     * ① 绕过 service 直接改库（运维 SQL、数据清洗任务）；
     * ② 缓存值因类重命名/字段变更等原因无法反序列化——此时键会一直存在且读取必然失败，
     * 没有 TTL 就只能靠人工 flush（兄弟项目 nexus-shop 2026-09-21 的
     * {@code city_list_tree} 无 TTL 事故正是如此）。</p>
     */
    private static final Duration FULL_TREE_TTL = Duration.ofHours(12);

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
     * <p><b>无任何条件</b>时走全量树缓存（{@link #selectFullTree()}）；<b>带条件</b>时仍每次查库
     * ——把过滤结果也塞进同一个 key 会让不同条件互相污染（后一次请求读到前一次的结果）。</p>
     *
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea) {
        boolean noCondition = sysDicArea.getAreaId() == null && sysDicArea.getAreaName() == null
            && sysDicArea.getLayered() == null && sysDicArea.getParentAreaId() == null;
        if (noCondition) {
            return selectFullTree();
        }
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        // 过滤结果集可能不含下级，故 hasChildren 一律以库为准（一次 0.6ms 的 IN 索引查询）
        markHasChildrenByDb(areas);
        if (sysDicArea.getParentAreaId() != null) {
            return areas;
        }
        return buildTree(areas);
    }

    /**
     * 查询全量行政区划树（带 TTL 缓存）。
     * <p>缓存 key 为单键（{@link #FULL_TREE_CACHE_KEY}），写路径（增/改/删）成功后主动清除，
     * 另配 {@link #FULL_TREE_TTL} 兜底。</p>
     *
     * <p><b>返回值为只读</b>：命中缓存时返回的就是缓存中的同一个实例，调用方不得修改
     * （尤其不得把返回的 VO 再交给 {@link #buildTree}——它会就地重置 {@code children}）。</p>
     *
     * <p>缓存读写在 Redis 异常时不做降级：本项目鉴权（{@code PlusSaTokenDao}）本身就走 Redis，
     * Redis 不可用时请求早已在鉴权阶段失败，此处再包一层 try/catch 只会掩盖问题。</p>
     *
     * @return 全量行政区划树
     */
    @Override
    public List<SysDicAreaVo> selectFullTree() {
        List<SysDicAreaVo> cached = RedisUtils.getCacheObject(FULL_TREE_CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        List<SysDicAreaVo> areas = selectSysDicAreaList(new SysDicArea());
        // 全表在手：直接用内存中的父子关系推导 hasChildren，省掉一次查询
        markHasChildrenFromCompleteSet(areas);
        List<SysDicAreaVo> tree = buildTree(areas);
        RedisUtils.setCacheObject(FULL_TREE_CACHE_KEY, tree, FULL_TREE_TTL);
        return tree;
    }

    /**
     * 根据条件查询行政区划列表。
     * @param sysDicArea 查询条件（行政区划）
     * @return 匹配条件的行政区划列表
     */
    @Override
    public List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(sysDicArea);
        markHasChildrenByDb(areas);
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
     * <p><b>为什么不复用 {@link #selectFullTree()} 的缓存</b>：本方法需要的是<b>扁平</b>列表
     * （{@link #collectSubtreeIds} 要按 parent 建反向索引），而缓存里存的是<b>已建好的树</b>。
     * 要把树还原成扁平列表再过滤重建，就必须先把每个 VO 深拷贝一份——因为 {@link #buildTree} 会
     * 就地重置入参的 {@code children}，直接把缓存实例喂回去会把缓存里的树打断成错误结构。
     * 本接口是管理端"选择上级"下拉（低频），为此每次深拷贝 1600+ 个对象并不划算，
     * 因此这里保持每次查库（安全、简单）。</p>
     *
     * @param areaId 待排除节点的行政区划代码，可为空（为空则返回全树）
     * @return 排除后的行政区划树
     */
    @Override
    public List<SysDicAreaVo> selectTreeExcludeSubtree(Long areaId) {
        List<SysDicAreaVo> areas = selectSysDicAreaList(new SysDicArea());
        markHasChildrenFromCompleteSet(areas);
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
        // 注意：remaining 已非完整结果集，此处沿用过滤前按全表算出的 hasChildren。
        // 被排除子树的父节点会保留"有下级"的箭头（点开为空），属可接受的降级——本接口已不再被
        // 前端调用（前端改用 /subtreeIds 懒加载），不值得为此再查一次库。
        return buildTree(remaining);
    }

    /**
     * 查询指定节点及其整棵子树的 areaId 集合。
     * <p>供前端"选择上级"懒加载下拉做防环：把这些节点置为 {@code disabled}，用户就选不到自己或自己的下级。
     * 相比 {@link #selectTreeExcludeSubtree(Long)} 返回整棵树（约 1.18MB），本方法只回一串 id
     * （省份级最大约 1500 个、区县级通常几十个），前端拿一次即可覆盖整棵懒加载树。</p>
     *
     * <p><b>代价</b>：需要一次全表查询（5112 行，实测 ~2ms）在内存里做 BFS，与
     * {@link #selectTreeExcludeSubtree(Long)} 相同；但每次打开弹窗只调一次，而不是每展开一级调一次。</p>
     *
     * @param areaId 起始节点的行政区划代码（非主键 pkid），可为空（为空返回空集合）
     * @return 含起始节点及其全部下级的 areaId 列表
     */
    @Override
    public List<Long> selectSubtreeAreaIds(Long areaId) {
        if (areaId == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(collectSubtreeIds(areaId, selectSysDicAreaList(new SysDicArea())));
    }

    /**
     * 新增行政区划。
     * @param bo 待新增的行政区划
     * @return 受影响行数
     */
    @Override
    public int insertSysDicArea(SysDicAreaBo bo) {
        validate(bo);
        SysDicArea entity = MapstructUtils.convert(bo, SysDicArea.class);
        int rows = baseMapper.insert(entity);
        evictFullTreeCache();
        return rows;
    }

    /**
     * 更新行政区划。
     * @param bo 待更新的行政区划
     * @return 受影响行数
     */
    @Override
    public int updateSysDicArea(SysDicAreaBo bo) {
        validate(bo);
        SysDicArea entity = MapstructUtils.convert(bo, SysDicArea.class);
        int rows = baseMapper.updateById(entity);
        evictFullTreeCache();
        return rows;
    }

    /**
     * 写入前校验：代码唯一、父级存在、层级连续、不得成环。
     * <p>唯一性校验绕过逻辑删除（见 {@link SysDicAreaMapper#countByAreaIdIncludeDeleted}），
     * 避免"删除后重建同码"产生重复 area_id。</p>
     *
     * <p><b>存量脏数据的兼容策略</b>：现有 1661 行中有 106 行的 {@code parent_area_id} 指向不存在的
     * 节点（34 个省级行的 {@code parent_area_id=156} 即属此类，156 这个 area_id 并不存在）。
     * 若一律拒绝，这些行将完全无法编辑。因此"父级存在性"仅在<b>本次确实修改了上级</b>时强制；
     * 未改动上级的编辑放行并打 WARN，避免新校验把存量数据锁死。数据本身由后续的数据清洗任务修复。</p>
     *
     * @param bo 待写入的行政区划
     */
    private void validate(SysDicAreaBo bo) {
        if (bo.getLayered() == null) {
            throw new ServiceException("层级不能为空");
        }

        // 1. area_id 唯一（含已逻辑删除的行，排除自身）
        long occupied = baseMapper.countByAreaIdIncludeDeleted(
            bo.getAreaId(), bo.getPkid() != null ? bo.getPkid() : -1L);
        if (occupied > 0) {
            throw new ServiceException("行政区划代码已存在（可能被已删除的记录占用）：" + bo.getAreaId());
        }

        // 2. 顶级节点
        if (bo.getParentAreaId() == null || bo.getParentAreaId() == 0L) {
            if (bo.getLayered() != 1L) {
                throw new ServiceException("顶级行政区划的层级必须为 1");
            }
            return;
        }

        // 3. 父级必须存在，且层级必须为父级 + 1
        SysDicArea existing = bo.getPkid() != null ? baseMapper.selectById(bo.getPkid()) : null;
        boolean parentChanged = existing == null
            || !bo.getParentAreaId().equals(existing.getParentAreaId());
        SysDicArea parent = baseMapper.selectOne(new LambdaQueryWrapper<SysDicArea>()
            .eq(SysDicArea::getAreaId, bo.getParentAreaId()));
        if (parent == null) {
            if (parentChanged) {
                throw new ServiceException("上级行政区划不存在：" + bo.getParentAreaId());
            }
            // 存量脏数据且本次未改上级：放行，仅告警，避免锁死编辑
            log.warn("行政区划 areaId={} 的上级 areaId={} 不存在（存量脏数据），本次未修改上级，跳过存在性与层级校验",
                bo.getAreaId(), bo.getParentAreaId());
            return;
        }
        if (parent.getLayered() == null) {
            throw new ServiceException("上级行政区划层级缺失，无法校验层级连续性：areaId=" + parent.getAreaId());
        }
        if (bo.getLayered() != parent.getLayered() + 1) {
            throw new ServiceException("层级必须为上级层级 + 1（上级 "
                + parent.getLayered() + "，当前 " + bo.getLayered() + "）");
        }

        // 4. 修改时父级不能是自己或自己的下级（防环）
        if (existing != null && existing.getAreaId() != null) {
            if (existing.getAreaId().equals(bo.getParentAreaId())) {
                throw new ServiceException("上级行政区划不能是自己");
            }
            Set<Long> subtree = collectSubtreeIds(existing.getAreaId(), selectSysDicAreaList(new SysDicArea()));
            if (subtree.contains(bo.getParentAreaId())) {
                throw new ServiceException("上级行政区划不能是自己的下级");
            }
        }
    }

    /**
     * 批量删除行政区划（逻辑删除）。
     * <p>删除前校验是否存在下级：存在则整体拒绝。校验与删除在同一事务内，
     * 任一节点被拒则全部回滚。</p>
     *
     * <p><b>语义说明</b>：
     * <ul>
     *   <li>子节点检查只统计<b>未删除</b>的下级（{@code exists} 由 {@code @TableLogic} 自动追加
     *       {@code del_flag='0'}）。因此已逻辑删除的下级不会阻止父节点删除——这是刻意为之，
     *       否则被软删的子树会永久锁死其祖先。</li>
     *   <li>本校验是<b>尽力而为</b>，不是并发安全的：它无法阻止"校验通过后、删除提交前"
     *       另一事务插入新下级。该窗口很小（管理端接口、前端逐条删除），如需彻底关闭，
     *       应在插入与删除两侧共用同一把分布式锁（Lock4j 已可用），锁键取行政区划代码。</li>
     * </ul>
     *
     * @param pkids 待删除的主键数组
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSysDicAreaByPkids(Long[] pkids) {
        if (pkids == null || pkids.length == 0) {
            return 0;
        }
        for (Long pkid : pkids) {
            SysDicArea self = baseMapper.selectById(pkid);
            if (self == null || self.getAreaId() == null) {
                continue;
            }
            boolean hasChild = baseMapper.exists(new LambdaQueryWrapper<SysDicArea>()
                .eq(SysDicArea::getParentAreaId, self.getAreaId()));
            if (hasChild) {
                String label = self.getAreaName() != null ? self.getAreaName() : String.valueOf(self.getAreaId());
                throw new ServiceException("存在下级行政区划，不允许删除：" + label);
            }
        }
        int rows = baseMapper.deleteByIds(Arrays.asList(pkids));
        // 必须放在删除成功之后：上面的子节点校验抛 ServiceException 时缓存不应被清掉（清了也无害，但没必要）
        evictFullTreeCache();
        return rows;
    }

    /**
     * 清除全量树缓存。
     * <p>仅在写库调用返回后执行，保证校验/写入失败时缓存不被无谓清空。</p>
     */
    private void evictFullTreeCache() {
        RedisUtils.deleteObject(FULL_TREE_CACHE_KEY);
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
     * 依据库中真实下级回填 {@code hasChildren}（一次 {@code IN} 索引查询）。
     *
     * <p><b>为什么需要它</b>：懒加载表格与 {@code el-tree-select} 只给"有下级"的行渲染展开箭头。
     * 前端此前按 {@code layered < 5} 猜，而库里 5112 行只有 470 行真有下级 —— 4640 行显示假箭头、
     * 点开返回空列表，每个都是一次多余的往返。</p>
     *
     * <p><b>为什么是"按本页 IN"而不是全表 {@code group by}</b>：全表分组要扫完整个
     * {@code idx_parent_area_id}（实测 4.8ms），而本页 IN 只做几十到几百次索引查找
     * （34 个省级实测 <b>0.6ms</b>）。过滤查询命中量大时 IN 列表会长一些，但仍远小于全表扫描。</p>
     *
     * <p>查不到（或查询异常返回空）时统一置 false：宁可少一个箭头（用户还能靠搜索找到下级），
     * 也不要多出点了没反应的假箭头。</p>
     *
     * @param areas 待回填的节点（扁平列表即可，建树前后都可调用；本方法不读 {@code children}）
     */
    private void markHasChildrenByDb(List<SysDicAreaVo> areas) {
        if (CollUtil.isEmpty(areas)) {
            return;
        }
        List<Long> areaIds = areas.stream()
            .map(SysDicAreaVo::getAreaId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (areaIds.isEmpty()) {
            return;
        }
        List<Long> parentIds = baseMapper.selectParentAreaIdsHavingChildren(areaIds);
        Set<Long> withChildren = CollUtil.isEmpty(parentIds) ? Collections.emptySet() : new HashSet<>(parentIds);
        for (SysDicAreaVo area : areas) {
            area.setHasChildren(area.getAreaId() != null && withChildren.contains(area.getAreaId()));
        }
    }

    /**
     * 用<b>完整结果集</b>在内存里推导 {@code hasChildren}（不查库）。
     *
     * <p><b>前提</b>：入参必须包含全部未删除行，否则"某节点的 areaId 不在 parentAreaId 集合中"
     * 可能只是因为它不在结果集里。目前只有两个调用点满足：{@link #selectFullTree()}（全表建树前）
     * 与 {@link #selectTreeExcludeSubtree(Long)}（全表过滤前）。<b>过滤场景请用
     * {@link #markHasChildrenByDb(List)}</b>。</p>
     *
     * @param areas 全量扁平列表（建树前后均可，本方法不读 {@code children}）
     */
    private void markHasChildrenFromCompleteSet(List<SysDicAreaVo> areas) {
        if (CollUtil.isEmpty(areas)) {
            return;
        }
        Set<Long> parentIds = new HashSet<>();
        for (SysDicAreaVo area : areas) {
            if (area.getParentAreaId() != null) {
                parentIds.add(area.getParentAreaId());
            }
        }
        for (SysDicAreaVo area : areas) {
            area.setHasChildren(area.getAreaId() != null && parentIds.contains(area.getAreaId()));
        }
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
