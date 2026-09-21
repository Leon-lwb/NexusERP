package com.nexus.iot.firmware.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.domain.IotFirmwareStatus;
import com.nexus.iot.firmware.domain.bo.IotFirmwareBo;
import com.nexus.iot.firmware.mapper.IotFirmwareMapper;
import com.nexus.iot.firmware.service.impl.IotFirmwareServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IoT 固件登记单元测试（OTA MVP）。
 * <p>
 * 覆盖：固件 CRUD 与发布状态机（DRAFT→RELEASED→DISABLED）、服务端强制字段（状态/发布时间/
 * 产品型号归一化/校验值小写）、唯一键前置校验、已发布固件冻结（不可编辑/不可删除）、CAS 冲突。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotFirmwareServiceTest {

    private IotFirmwareMapper baseMapper;
    private IotFirmwareServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), IotFirmware.class);
        baseMapper = mock(IotFirmwareMapper.class);
        service = new IotFirmwareServiceImpl(baseMapper);
    }

    private IotFirmwareBo bo() {
        IotFirmwareBo bo = new IotFirmwareBo();
        bo.setFirmwareName("主控固件");
        bo.setVersion("1.2.0");
        bo.setFileOssId("oss-1001");
        bo.setChecksum("ABC123");
        bo.setFileSize(2048L);
        return bo;
    }

    private IotFirmware firmware(String status) {
        IotFirmware fw = new IotFirmware();
        fw.setFirmwareId(11L);
        fw.setFirmwareName("主控固件");
        fw.setVersion("1.2.0");
        fw.setProductKey("");
        fw.setFileOssId("oss-1001");
        fw.setChecksum("abc123");
        fw.setStatus(status);
        return fw;
    }

    /** 登记固件：服务端强制 DRAFT、发布时间为空、空产品型号归一化为空串、校验值小写、默认 MD5。 */
    @Test
    void insertByBo_forcesDraftAndNormalizes() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(IotFirmware.class))).thenReturn(1);

        assertEquals(1, service.insertByBo(bo()));

        ArgumentCaptor<IotFirmware> captor = ArgumentCaptor.forClass(IotFirmware.class);
        verify(baseMapper).insert(captor.capture());
        IotFirmware saved = captor.getValue();
        assertEquals(IotFirmwareStatus.DRAFT, saved.getStatus(), "客户端不可决定发布状态，新建必须为 DRAFT");
        assertNull(saved.getReleaseTime(), "未发布不得有发布时间");
        assertEquals("", saved.getProductKey(), "空产品型号必须归一化为空串（唯一索引对 NULL 不去重）");
        assertEquals("MD5", saved.getChecksumType(), "校验算法默认 MD5");
        assertEquals("abc123", saved.getChecksum(), "校验值统一小写");
    }

    /** 同产品型号 + 版本号重复 → 拒绝（uk_version 的业务前置校验）。 */
    @Test
    void insertByBo_duplicateVersion_rejected() {
        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo()));
        assertTrue(ex.getMessage().contains("版本号已存在"));
        verify(baseMapper, never()).insert(any(IotFirmware.class));
    }

    /** 已发布固件不可编辑（版本号与固件包冻结）。 */
    @Test
    void updateByBo_releasedFirmware_rejected() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));
        IotFirmwareBo bo = bo();
        bo.setFirmwareId(11L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("仅草稿"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 草稿可编辑：CAS 更新，且服务端把状态钉回 DRAFT。 */
    @Test
    @SuppressWarnings("unchecked")
    void updateByBo_draft_updatesAndKeepsDraft() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.DRAFT));
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.update(any(), any())).thenReturn(1);
        IotFirmwareBo bo = bo();
        bo.setFirmwareId(11L);
        bo.setVersion("1.2.1");

        assertEquals(1, service.updateByBo(bo));

        ArgumentCaptor<Wrapper<IotFirmware>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotFirmware> wrapper = (LambdaUpdateWrapper<IotFirmware>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotFirmwareStatus.DRAFT));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("1.2.1"));
    }

    /** 发布：DRAFT → RELEASED，写入发布时间。 */
    @Test
    @SuppressWarnings("unchecked")
    void changeStatus_draftToReleased_setsReleaseTime() {
        when(baseMapper.selectById(11L))
            .thenReturn(firmware(IotFirmwareStatus.DRAFT), firmware(IotFirmwareStatus.RELEASED));
        when(baseMapper.update(any(), any())).thenReturn(1);

        IotFirmware result = service.changeStatus(11L, IotFirmwareStatus.RELEASED, "首发");

        assertEquals(IotFirmwareStatus.RELEASED, result.getStatus());
        ArgumentCaptor<Wrapper<IotFirmware>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        LambdaUpdateWrapper<IotFirmware> wrapper = (LambdaUpdateWrapper<IotFirmware>) captor.getValue();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(IotFirmwareStatus.RELEASED));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("首发"));
    }

    /** 发布前必须有固件包与校验值。 */
    @Test
    void changeStatus_releaseWithoutChecksum_rejected() {
        IotFirmware fw = firmware(IotFirmwareStatus.DRAFT);
        fw.setChecksum("");
        when(baseMapper.selectById(11L)).thenReturn(fw);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.changeStatus(11L, IotFirmwareStatus.RELEASED, null));
        assertTrue(ex.getMessage().contains("校验值"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 非法流转：已发布不可回退草稿。 */
    @Test
    void changeStatus_releasedToDraft_rejected() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.changeStatus(11L, IotFirmwareStatus.DRAFT, null));
        assertTrue(ex.getMessage().contains("非法"));
        verify(baseMapper, never()).update(any(), any());
    }

    /** 停用后可重新发布：DISABLED → RELEASED。 */
    @Test
    void changeStatus_disabledToReleased_allowed() {
        when(baseMapper.selectById(11L))
            .thenReturn(firmware(IotFirmwareStatus.DISABLED), firmware(IotFirmwareStatus.RELEASED));
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertEquals(IotFirmwareStatus.RELEASED,
            service.changeStatus(11L, IotFirmwareStatus.RELEASED, "恢复").getStatus());
    }

    /** CAS 冲突：影响 0 行 → 抛异常（状态被并发变更）。 */
    @Test
    void changeStatus_casConflict_throws() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.DRAFT));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.changeStatus(11L, IotFirmwareStatus.DISABLED, null));
        assertTrue(ex.getMessage().contains("已被其他操作变更"));
    }

    /** 已发布固件不可删除。 */
    @Test
    void deleteByIds_releasedFirmware_rejected() {
        when(baseMapper.selectList(any())).thenReturn(List.of(firmware(IotFirmwareStatus.RELEASED)));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{11L}));
        assertTrue(ex.getMessage().contains("已发布"));
        verify(baseMapper, never()).delete(any());
    }

    /** 草稿可删除。 */
    @Test
    void deleteByIds_draft_deletes() {
        when(baseMapper.selectList(any())).thenReturn(List.of(firmware(IotFirmwareStatus.DRAFT)));
        when(baseMapper.delete(any())).thenReturn(1);

        assertEquals(1, service.deleteByIds(new Long[]{11L}));
    }

    /** 未发布/已停用固件不可用于下发升级任务。 */
    @Test
    void requireReleased_disabledFirmware_rejected() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.DISABLED));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.requireReleased(11L));
        assertTrue(ex.getMessage().contains("RELEASED"));
    }

    /** 已发布固件可用于下发。 */
    @Test
    void requireReleased_releasedFirmware_ok() {
        when(baseMapper.selectById(11L)).thenReturn(firmware(IotFirmwareStatus.RELEASED));

        assertEquals("1.2.0", service.requireReleased(11L).getVersion());
    }
}
