package com.nexus.iot.device.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.linpeilie.Converter;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.group.mapper.IotDeviceGroupRelMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagRelMapper;
import com.nexus.iot.device.group.service.IotDeviceFilterResolver;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.impl.IotDeviceServiceImpl;
import com.nexus.iot.mqtt.IotMqttAuthenticator;
import com.nexus.iot.pipeline.IotDeviceReportTimeCache;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IoT 设备生命周期单元测试（IOT-DEV-01 / §4#7）。
 * <p>
 * 覆盖（均为"修复前会失败"的行为断言）：
 * <ul>
 *   <li>激活/禁用/注销走条件更新（CAS），0 行抛业务异常；</li>
 *   <li>注销为终态，不允许复活；同状态流转被拒；</li>
 *   <li>禁用后 MQTT 认证被拒（与 {@code IotMqttAuthenticator} 状态校验对齐）；</li>
 *   <li>{@code updateByBo} 服务端强制清空 status/connectionStatus/deviceSecret（前端无法直改状态）；</li>
 *   <li>运维详情聚合在线状态、最后上报时间、固件版本与分组标签。</li>
 * </ul>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotDeviceLifecycleTest {

    private static final String TENANT = "000000";

    private IotDeviceMapper deviceMapper;
    private IotDeviceServiceImpl deviceService;
    private IotMqttAuthenticator authenticator;
    private Converter converter;

    private MockedStatic<LoginHelper> loginMock;
    private MockedStatic<TenantHelper> tenantMock;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotDevice.class);
        deviceMapper = mock(IotDeviceMapper.class);
        IotProductMapper productMapper = mock(IotProductMapper.class);
        IotDeviceGroupRelMapper groupRelMapper = mock(IotDeviceGroupRelMapper.class);
        IotDeviceTagRelMapper tagRelMapper = mock(IotDeviceTagRelMapper.class);
        IotDeviceFilterResolver filterResolver = mock(IotDeviceFilterResolver.class);
        IotDeviceReportTimeCache reportTimeCache = mock(IotDeviceReportTimeCache.class);
        converter = mock(Converter.class);
        when(converter.convert(any(IotDeviceBo.class), eq(IotDevice.class))).thenAnswer(inv -> {
            IotDeviceBo bo = inv.getArgument(0);
            IotDevice d = new IotDevice();
            d.setDeviceId(bo.getDeviceId());
            d.setDeviceCode(bo.getDeviceCode());
            d.setDeviceName(bo.getDeviceName());
            d.setProductId(bo.getProductId());
            d.setProtocolType(bo.getProtocolType());
            d.setStatus(bo.getStatus());
            d.setConnectionStatus(bo.getConnectionStatus());
            return d;
        });
        deviceService = new IotDeviceServiceImpl(deviceMapper, productMapper, groupRelMapper, tagRelMapper,
            filterResolver, reportTimeCache, converter);
        authenticator = new IotMqttAuthenticator(deviceService);

        loginMock = mockStatic(LoginHelper.class);
        loginMock.when(LoginHelper::getUserId).thenReturn(9L);
        loginMock.when(LoginHelper::getUsername).thenReturn("admin");
        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(TenantHelper::getTenantId).thenReturn(TENANT);
    }

    @AfterEach
    void tearDown() {
        loginMock.close();
        tenantMock.close();
    }

    private IotDevice device(String status) {
        IotDevice d = new IotDevice();
        d.setDeviceId(1L);
        d.setTenantId(TENANT);
        d.setDeviceCode("DEV0001");
        d.setDeviceSecret("SECRET1");
        d.setStatus(status);
        d.setConnectionStatus("ONLINE");
        d.setProductId(4L);
        d.setFirmwareVersion("V1.0.0");
        return d;
    }

    /** 禁用：ACTIVE → INACTIVE，CAS 成功并强制置 OFFLINE。 */
    @Test
    @SuppressWarnings("unchecked")
    void changeStatus_activeToInactive_casAndForcesOffline() {
        when(deviceMapper.selectById(1L)).thenReturn(device(IotDeviceStatus.ACTIVE));
        when(deviceMapper.update(any(), any())).thenReturn(1);

        int rows = deviceService.changeStatus(1L, IotDeviceStatus.INACTIVE, "计划检修");

        assertEquals(1, rows);
        ArgumentCaptor<Wrapper<IotDevice>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deviceMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotDevice> wrapper = (LambdaUpdateWrapper<IotDevice>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotDeviceStatus.INACTIVE), "必须写入目标状态");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("OFFLINE"), "禁用后必须置连接状态 OFFLINE");
        assertTrue(wrapper.getParamNameValuePairs().containsValue("计划检修"), "变更原因需落库便于审计");
    }

    /** 激活：INACTIVE → ACTIVE。 */
    @Test
    void changeStatus_inactiveToActive_ok() {
        when(deviceMapper.selectById(1L)).thenReturn(device(IotDeviceStatus.INACTIVE));
        when(deviceMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, deviceService.changeStatus(1L, IotDeviceStatus.ACTIVE, null));
    }

    /** 注销为终态：DECOMMISSIONED → ACTIVE 被拒（不允许复活）。 */
    @Test
    void changeStatus_decommissionedIsTerminal_rejected() {
        when(deviceMapper.selectById(1L)).thenReturn(device(IotDeviceStatus.DECOMMISSIONED));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> deviceService.changeStatus(1L, IotDeviceStatus.ACTIVE, null));
        assertTrue(ex.getMessage().contains("非法流转"));
        verify(deviceMapper, never()).update(any(), any());
    }

    /** 同状态流转（无变化）被拒，避免无意义写入与误判。 */
    @Test
    void changeStatus_sameStatus_rejected() {
        when(deviceMapper.selectById(1L)).thenReturn(device(IotDeviceStatus.ACTIVE));
        assertThrows(ServiceException.class,
            () -> deviceService.changeStatus(1L, IotDeviceStatus.ACTIVE, null));
    }

    /** 非法目标状态被拒（服务端二次校验，不依赖 BO 的 @Pattern）。 */
    @Test
    void changeStatus_unknownTarget_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> deviceService.changeStatus(1L, "DELETED", null));
        assertTrue(ex.getMessage().contains("非法"));
    }

    /** CAS 冲突：条件更新影响 0 行 → 抛"已被其他操作变更"。 */
    @Test
    void changeStatus_casConflict_throws() {
        when(deviceMapper.selectById(1L)).thenReturn(device(IotDeviceStatus.ACTIVE));
        when(deviceMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> deviceService.changeStatus(1L, IotDeviceStatus.INACTIVE, null));
        assertTrue(ex.getMessage().contains("已被其他操作变更"));
    }

    /** 设备不存在 → 抛业务异常。 */
    @Test
    void changeStatus_deviceMissing_throws() {
        when(deviceMapper.selectById(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> deviceService.changeStatus(1L, IotDeviceStatus.INACTIVE, null));
    }

    /** 禁用后 MQTT 认证必须被拒（认证只放行 ACTIVE）。 */
    @Test
    void mqttAuth_inactiveDevice_rejected() {
        when(deviceMapper.selectOne(any())).thenReturn(device(IotDeviceStatus.INACTIVE));

        boolean valid = authenticator.checkValid("DEV0001", "DEV0001", "SECRET1".getBytes());

        assertFalse(valid, "已禁用设备的 MQTT 认证必须被拒绝");
    }

    /** 激活状态下密钥正确 → 认证通过（确保上面的拒绝不是"永远拒绝"造成的假阳性）。 */
    @Test
    void mqttAuth_activeDeviceWithCorrectSecret_passed() {
        when(deviceMapper.selectOne(any())).thenReturn(device(IotDeviceStatus.ACTIVE));

        assertTrue(authenticator.checkValid("DEV0001", "DEV0001", "SECRET1".getBytes()));
        assertFalse(authenticator.checkValid("DEV0001", "DEV0001", "WRONG".getBytes()));
    }

    /** 编辑设备：服务端强制清空 status/connectionStatus/deviceSecret（前端直改状态被堵死）。 */
    @Test
    void updateByBo_serverSideWhitelist_clearsAuthoritativeFields() {
        IotDeviceBo bo = new IotDeviceBo();
        bo.setDeviceId(1L);
        bo.setDeviceCode("DEV0001");
        bo.setDeviceName("改名");
        bo.setProductId(4L);
        bo.setStatus(IotDeviceStatus.DECOMMISSIONED);
        bo.setConnectionStatus("ONLINE");
        when(deviceMapper.updateById(any(IotDevice.class))).thenReturn(1);

        deviceService.updateByBo(bo);

        ArgumentCaptor<IotDevice> captor = ArgumentCaptor.forClass(IotDevice.class);
        verify(deviceMapper).updateById(captor.capture());
        assertNull(captor.getValue().getStatus(), "status 必须被服务端清空（只能走生命周期端点）");
        assertNull(captor.getValue().getConnectionStatus(), "connectionStatus 由 Broker 独占维护");
        assertNull(captor.getValue().getDeviceSecret(), "deviceSecret 只能走重置密钥端点");
    }

    /** 新增设备：固定 ACTIVE + OFFLINE，忽略客户端传入的状态。 */
    @Test
    void insertByBo_forcesActiveAndOffline() {
        IotDeviceBo bo = new IotDeviceBo();
        bo.setDeviceCode("DEV0002");
        bo.setDeviceName("新设备");
        bo.setProductId(4L);
        bo.setProtocolType("MQTT");
        bo.setStatus(IotDeviceStatus.DECOMMISSIONED);
        bo.setConnectionStatus("ONLINE");
        when(deviceMapper.insert(any(IotDevice.class))).thenReturn(1);

        deviceService.insertByBo(bo);

        ArgumentCaptor<IotDevice> captor = ArgumentCaptor.forClass(IotDevice.class);
        verify(deviceMapper).insert(captor.capture());
        assertEquals(IotDeviceStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("OFFLINE", captor.getValue().getConnectionStatus());
        assertTrue(captor.getValue().getDeviceSecret() != null && !captor.getValue().getDeviceSecret().isBlank(),
            "新增设备必须自动生成密钥");
    }

    /** 运维详情：复用既有列 + Redis 最后上报时间 + 分组/标签集合。 */
    @Test
    void selectOps_aggregatesOpsFields() {
        IotDevice d = device(IotDeviceStatus.ACTIVE);
        d.setLastOnlineTime(new Date(1000L));
        when(deviceMapper.selectById(1L)).thenReturn(d);
        when(deviceMapper.selectById(1L)).thenReturn(d);

        // 通过重放构造：直接验证 VO 聚合字段（分组/标签 mapper 为 mock，返回空列表）
        var vo = deviceService.selectOps(1L);

        assertEquals("DEV0001", vo.getDeviceCode());
        assertEquals(IotDeviceStatus.ACTIVE, vo.getStatus());
        assertEquals("ONLINE", vo.getConnectionStatus());
        assertEquals("V1.0.0", vo.getFirmwareVersion());
        assertEquals(new Date(1000L), vo.getLastOnlineTime());
        assertEquals(List.of(), vo.getGroupIds());
        assertEquals(List.of(), vo.getTagIds());
    }

    /** 查询条件：分组筛选命中空集时必须"查不到"，而不是忽略条件返回全量。 */
    @Test
    void selectPageList_emptyGroupFilter_producesNoMatchCondition() {
        IotDeviceFilterResolver resolver = mock(IotDeviceFilterResolver.class);
        when(resolver.resolveDeviceIds(eq(100L), any(), any())).thenReturn(List.of());
        IotDeviceServiceImpl service = new IotDeviceServiceImpl(deviceMapper, mock(IotProductMapper.class),
            mock(IotDeviceGroupRelMapper.class), mock(IotDeviceTagRelMapper.class), resolver,
            mock(IotDeviceReportTimeCache.class), converter);
        when(deviceMapper.selectVoPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        IotDeviceBo bo = new IotDeviceBo();
        bo.setGroupId(100L);
        service.selectPageList(bo, new com.nexus.common.mybatis.core.page.PageQuery(1, 10));

        ArgumentCaptor<Wrapper<IotDevice>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deviceMapper).selectVoPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("1 = 0"),
            "空集筛选必须翻译为恒假条件: " + captor.getValue().getSqlSegment());
    }

    /**
     * 租户上下文静态桩自检（不调用任何 Mockito 匹配器，避免匹配器栈泄漏到后续用例）。
     */
    @Test
    void tenantHelperStubSanity() {
        assertEquals(TENANT, TenantHelper.getTenantId());
    }
}
