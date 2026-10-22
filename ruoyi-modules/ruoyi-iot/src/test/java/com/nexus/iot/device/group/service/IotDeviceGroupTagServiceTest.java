package com.nexus.iot.device.group.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.group.domain.IotDeviceGroup;
import com.nexus.iot.device.group.domain.IotDeviceGroupRel;
import com.nexus.iot.device.group.domain.IotDeviceTag;
import com.nexus.iot.device.group.domain.IotDeviceTagRel;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupAssignBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceGroupBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBindBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceGroupVo;
import com.nexus.iot.device.group.mapper.IotDeviceGroupMapper;
import com.nexus.iot.device.group.mapper.IotDeviceGroupRelMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagRelMapper;
import com.nexus.iot.device.group.service.impl.IotDeviceGroupServiceImpl;
import com.nexus.iot.device.group.service.impl.IotDeviceTagServiceImpl;
import com.nexus.iot.device.mapper.IotDeviceMapper;

import io.github.linpeilie.Converter;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

/**
 * IoT 设备分组 / 标签 服务单元测试（§4#2 功能缺失清单）。
 * <p>
 * 覆盖：分组树 ancestors 计算与父级变更后的后代重写、删除前置校验（子分组/已挂设备）、
 * 批量挂分组与批量打标签的幂等、按分组（含子树）+ 标签筛选的集合语义、标签删除级联清关系。
 * <p>
 * 说明：{@code MapstructUtils.convert} 与 {@code TenantHelper} 均为静态工具，测试内静态打桩；
 * {@code RedisUtils} 未参与本链路（分组/标签不使用 Redis）。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotDeviceGroupTagServiceTest {

    private static final String TENANT = "000000";

    private IotDeviceGroupMapper groupMapper;
    private IotDeviceGroupRelMapper groupRelMapper;
    private IotDeviceTagMapper tagMapper;
    private IotDeviceTagRelMapper tagRelMapper;
    private IotDeviceMapper deviceMapper;
    private IotDeviceFilterResolver filterResolver;

    private IotDeviceGroupServiceImpl groupService;
    private IotDeviceTagServiceImpl tagService;

    private MockedStatic<TenantHelper> tenantMock;
    private Converter converter;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceGroup.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceGroupRel.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceTag.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDeviceTagRel.class);

        groupMapper = mock(IotDeviceGroupMapper.class);
        groupRelMapper = mock(IotDeviceGroupRelMapper.class);
        tagMapper = mock(IotDeviceTagMapper.class);
        tagRelMapper = mock(IotDeviceTagRelMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        filterResolver = new IotDeviceFilterResolver(groupMapper, groupRelMapper, tagRelMapper);

        converter = mock(Converter.class);
        groupService = new IotDeviceGroupServiceImpl(groupMapper, groupRelMapper, deviceMapper, filterResolver, converter);
        tagService = new IotDeviceTagServiceImpl(tagMapper, tagRelMapper, deviceMapper, converter);

        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(TenantHelper::getTenantId).thenReturn(TENANT);

        when(converter.convert(any(IotDeviceGroupBo.class), eq(IotDeviceGroup.class)))
            .thenAnswer(inv -> {
                IotDeviceGroupBo bo = inv.getArgument(0);
                IotDeviceGroup g = new IotDeviceGroup();
                g.setGroupId(bo.getGroupId());
                g.setParentId(bo.getParentId());
                g.setGroupCode(bo.getGroupCode());
                g.setGroupName(bo.getGroupName());
                g.setOrderNum(bo.getOrderNum());
                g.setStatus(bo.getStatus());
                return g;
            });
        when(converter.convert(any(IotDeviceTagBo.class), eq(IotDeviceTag.class)))
                .thenAnswer(
                        inv -> {
                            IotDeviceTagBo bo = inv.getArgument(0);
                            IotDeviceTag t = new IotDeviceTag();
                            t.setTagId(bo.getTagId());
                            t.setTagName(bo.getTagName());
                            t.setTagColor(bo.getTagColor());
                            return t;
                        });
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    private IotDeviceGroupBo groupBo(Long id, Long parentId, String code, String name) {
        IotDeviceGroupBo bo = new IotDeviceGroupBo();
        bo.setGroupId(id);
        bo.setParentId(parentId);
        bo.setGroupCode(code);
        bo.setGroupName(name);
        return bo;
    }

    private IotDeviceGroup group(Long id, Long parentId, String ancestors) {
        IotDeviceGroup g = new IotDeviceGroup();
        g.setGroupId(id);
        g.setParentId(parentId);
        g.setAncestors(ancestors);
        g.setTenantId(TENANT);
        return g;
    }

    /** 新增根分组：ancestors 固定为 "0"（物化路径根）。 */
    @Test
    void insert_rootGroup_ancestorsIsZero() {
        when(groupMapper.insert(any(IotDeviceGroup.class))).thenReturn(1);

        groupService.insertByBo(groupBo(null, null, "LINE-A", "A线"));

        ArgumentCaptor<IotDeviceGroup> captor = ArgumentCaptor.forClass(IotDeviceGroup.class);
        verify(groupMapper).insert(captor.capture());
        assertEquals("0", captor.getValue().getAncestors());
        assertEquals(0L, captor.getValue().getParentId());
        assertEquals("0", captor.getValue().getStatus());
    }

    /** 新增子分组：ancestors = 父 ancestors + 父ID（修复前无任何分组能力，本断言即功能核心）。 */
    @Test
    void insert_childGroup_ancestorsFromParent() {
        when(groupMapper.selectById(100L)).thenReturn(group(100L, 0L, "0"));
        when(groupMapper.insert(any(IotDeviceGroup.class))).thenReturn(1);

        groupService.insertByBo(groupBo(null, 100L, "LINE-A-1", "A线1段"));

        ArgumentCaptor<IotDeviceGroup> captor = ArgumentCaptor.forClass(IotDeviceGroup.class);
        verify(groupMapper).insert(captor.capture());
        assertEquals("0,100", captor.getValue().getAncestors());
    }

    /** 新增子分组时父分组不存在 → 业务异常（不产生孤儿分组）。 */
    @Test
    void insert_parentNotFound_throws() {
        when(groupMapper.selectById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> groupService.insertByBo(groupBo(null, 999L, "X", "X")));
        verify(groupMapper, never()).insert(any(IotDeviceGroup.class));
    }

    /** 父分组变更为自身 → 拒绝（防循环层级）。 */
    @Test
    void update_parentIsSelf_throws() {
        when(groupMapper.selectById(200L)).thenReturn(group(200L, 0L, "0"));
        ServiceException ex = assertThrows(ServiceException.class,
            () -> groupService.updateByBo(groupBo(200L, 200L, "G", "G")));
        assertTrue(ex.getMessage().contains("自身"));
    }

    /** 父分组变更 → 后代 ancestors 前缀被重写（否则子树筛选会漏/串）。 */
    @Test
    void update_parentChanged_rewritesDescendants() {
        IotDeviceGroup old = group(200L, 0L, "0");
        IotDeviceGroup newParent = group(100L, 0L, "0");
        IotDeviceGroup descendant = group(300L, 200L, "0,200");
        when(groupMapper.selectById(200L)).thenReturn(old);
        when(groupMapper.selectById(100L)).thenReturn(newParent);
        when(groupMapper.updateById(any(IotDeviceGroup.class))).thenReturn(1);
        when(groupMapper.selectList(any())).thenReturn(List.of(old, newParent, descendant));

        groupService.updateByBo(groupBo(200L, 100L, "G", "G"));

        ArgumentCaptor<IotDeviceGroup> captor = ArgumentCaptor.forClass(IotDeviceGroup.class);
        verify(groupMapper, times(2)).updateById(captor.capture());
        boolean descendantRewritten = captor.getAllValues().stream()
            .anyMatch(g -> Long.valueOf(300L).equals(g.getGroupId()) && "0,100,200".equals(g.getAncestors()));
        assertTrue(descendantRewritten, "后代分组的 ancestors 必须重写为 0,100,200");
    }

    /** 删除分组：存在子分组 → 拒绝。 */
    @Test
    void delete_withChildren_rejected() {
        when(groupMapper.selectCount(any())).thenReturn(2L);
        ServiceException ex = assertThrows(ServiceException.class, () -> groupService.deleteByIds(new Long[]{100L}));
        assertTrue(ex.getMessage().contains("子分组"));
        verify(groupMapper, never()).deleteBatchIds(any());
    }

    /** 删除分组：已挂设备 → 拒绝（避免产生孤儿关系）。 */
    @Test
    void delete_withBoundDevices_rejected() {
        when(groupMapper.selectCount(any())).thenReturn(0L);
        when(groupRelMapper.selectCount(any())).thenReturn(3L);
        ServiceException ex = assertThrows(ServiceException.class, () -> groupService.deleteByIds(new Long[]{100L}));
        assertTrue(ex.getMessage().contains("设备"));
    }

    /** 批量挂分组：已存在的关系跳过（幂等），仅新增缺失关系。 */
    @Test
    void assignDevices_skipsExistingRelations() {
        when(groupMapper.selectById(100L)).thenReturn(group(100L, 0L, "0"));
        IotDevice d1 = new IotDevice();
        d1.setDeviceId(1L);
        IotDevice d2 = new IotDevice();
        d2.setDeviceId(2L);
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of(d1, d2));
        IotDeviceGroupRel exists = new IotDeviceGroupRel();
        exists.setDeviceId(1L);
        exists.setGroupId(100L);
        when(groupRelMapper.selectList(any())).thenReturn(List.of(exists));
        when(groupRelMapper.insert(any(IotDeviceGroupRel.class))).thenReturn(1);

        IotDeviceGroupAssignBo bo = new IotDeviceGroupAssignBo();
        bo.setGroupId(100L);
        bo.setDeviceIds(List.of(1L, 2L));
        int rows = groupService.assignDevices(bo);

        assertEquals(1, rows, "设备1已挂载应跳过，仅新增设备2的关系");
        ArgumentCaptor<IotDeviceGroupRel> captor = ArgumentCaptor.forClass(IotDeviceGroupRel.class);
        verify(groupRelMapper).insert(captor.capture());
        assertEquals(2L, captor.getValue().getDeviceId());
        assertEquals(TENANT, captor.getValue().getTenantId(), "关系表必须显式写租户（消费/批处理路径不依赖插件）");
    }

    /** 批量挂分组：remove=true 走物理删除关系。 */
    @Test
    void assignDevices_remove_deletesRelations() {
        when(groupMapper.selectById(100L)).thenReturn(group(100L, 0L, "0"));
        IotDevice d1 = new IotDevice();
        d1.setDeviceId(1L);
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of(d1));
        when(groupRelMapper.delete(any())).thenReturn(1);

        IotDeviceGroupAssignBo bo = new IotDeviceGroupAssignBo();
        bo.setGroupId(100L);
        bo.setDeviceIds(List.of(1L));
        bo.setRemove(true);

        assertEquals(1, groupService.assignDevices(bo));
        verify(groupRelMapper, never()).insert(any(IotDeviceGroupRel.class));
    }

    /** 批量挂分组：设备不存在 → 拒绝（跨租户ID被插件视为不存在）。 */
    @Test
    void assignDevices_deviceMissing_throws() {
        when(groupMapper.selectById(100L)).thenReturn(group(100L, 0L, "0"));
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of());

        IotDeviceGroupAssignBo bo = new IotDeviceGroupAssignBo();
        bo.setGroupId(100L);
        bo.setDeviceIds(List.of(1L));
        assertThrows(ServiceException.class, () -> groupService.assignDevices(bo));
    }

    /** 标签绑定：幂等（已绑定组合跳过）。 */
    @Test
    void bindTags_idempotent() {
        IotDeviceTag t1 = new IotDeviceTag();
        t1.setTagId(11L);
        IotDeviceTag t2 = new IotDeviceTag();
        t2.setTagId(12L);
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(t1, t2));
        IotDevice d1 = new IotDevice();
        d1.setDeviceId(1L);
        when(deviceMapper.selectBatchIds(any())).thenReturn(List.of(d1));
        IotDeviceTagRel exists = new IotDeviceTagRel();
        exists.setDeviceId(1L);
        exists.setTagId(11L);
        when(tagRelMapper.selectList(any())).thenReturn(List.of(exists));
        when(tagRelMapper.insert(any(IotDeviceTagRel.class))).thenReturn(1);

        IotDeviceTagBindBo bo = new IotDeviceTagBindBo();
        bo.setDeviceIds(List.of(1L));
        bo.setTagIds(List.of(11L, 12L));

        assertEquals(1, tagService.bindTags(bo), "已绑定的 11 应跳过，仅新增 12");
    }

    /** 标签删除：级联清理设备-标签关系（否则脏关系残留导致筛选出已删标签）。 */
    @Test
    void deleteTag_cascadesRelations() {
        when(tagRelMapper.delete(any())).thenReturn(2);
        when(tagMapper.deleteBatchIds(any())).thenReturn(1);

        assertEquals(1, tagService.deleteByIds(new Long[]{11L}));
        verify(tagRelMapper).delete(any());
    }

    /** 标签不存在 → 绑定被拒。 */
    @Test
    void bindTags_tagMissing_throws() {
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of());
        IotDeviceTagBindBo bo = new IotDeviceTagBindBo();
        bo.setDeviceIds(List.of(1L));
        bo.setTagIds(List.of(11L));
        assertThrows(ServiceException.class, () -> tagService.bindTags(bo));
    }

    /** 无筛选条件 → 返回 null（表示"不限制"，调用方不得翻译成"查不到"）。 */
    @Test
    void resolveDeviceIds_noFilter_returnsNull() {
        assertNull(filterResolver.resolveDeviceIds(null, null, null));
        assertNull(filterResolver.resolveDeviceIds(null, Boolean.TRUE, List.of()));
    }

    /** 分组（含子树）+ 标签 → 设备ID取交集。 */
    @Test
    void resolveDeviceIds_groupSubtreeAndTag_intersect() {
        when(groupMapper.selectList(any())).thenReturn(List.of(
            group(100L, 0L, "0"),
            group(200L, 100L, "0,100"),
            group(300L, 0L, "0")));
        when(groupRelMapper.selectDeviceIdsByGroupIds(eq(TENANT), any())).thenReturn(List.of(1L, 2L, 3L));
        when(tagRelMapper.selectDeviceIdsByTagIds(eq(TENANT), any())).thenReturn(List.of(2L, 3L, 4L));

        List<Long> ids = filterResolver.resolveDeviceIds(100L, Boolean.TRUE, List.of(11L));

        assertNotNull(ids);
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(2L, 3L)), "应为分组子树∩标签 = {2,3}");
    }

    /** 子树收集：只收自身与后代，不串到兄弟分组。 */
    @Test
    void collectSelfAndDescendants_onlyOwnSubtree() {
        when(groupMapper.selectList(any())).thenReturn(List.of(
            group(100L, 0L, "0"),
            group(200L, 100L, "0,100"),
            group(300L, 200L, "0,100,200"),
            group(400L, 0L, "0")));

        List<Long> ids = filterResolver.collectSelfAndDescendants(100L);

        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of(100L, 200L, 300L)));
    }

    /** 分组列表：deviceCount 由一次 GROUP BY 查询填充。 */
    @Test
    @SuppressWarnings("unchecked")
    void selectList_fillsDeviceCount() {
        IotDeviceGroupVo vo = new IotDeviceGroupVo();
        vo.setGroupId(100L);
        when(groupMapper.selectVoList(any())).thenReturn(List.of(vo));
        when(groupRelMapper.countDevicesByGroupIds(eq(TENANT), any()))
                .thenReturn(List.of(Map.of("groupId", 100L, "deviceCount", 5L)));

        List<IotDeviceGroupVo> list = groupService.selectList(new IotDeviceGroupBo());

        assertEquals(1, list.size());
        assertEquals(5L, list.get(0).getDeviceCount());
    }
}
