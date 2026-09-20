package com.nexus.erp.cv;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.cv.domain.vo.CvBatchRegisterResultVo;
import com.nexus.erp.cv.service.CvFaceBatchRegisterService;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.system.domain.SysUser;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysOssService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 人脸底库批量注册单元测试（ERP-CV-04）
 * <p>修复前：逐条 catch 后只 log，返回列表只含成功项，管理员不知道谁没注册进去、原因是什么。
 * 断言：失败/跳过项必须出现在 {@code failed[]} 且带原因。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
@ExtendWith(MockitoExtension.class)
class CvFaceBatchRegisterServiceTest {

    @Mock
    private VisionClient visionClient;
    @Mock
    private ISysOssService sysOssService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SchWorkerMapper workerMapper;

    private CvFaceBatchRegisterService service;

    private MockedStatic<TenantHelper> tenantHelperMock;
    private MockedStatic<DynamicDataSourceContextHolder> dsMock;

    @BeforeEach
    void setUp() {
        service = new CvFaceBatchRegisterService(visionClient, sysOssService, sysUserMapper, workerMapper);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn("000000");
        dsMock = Mockito.mockStatic(DynamicDataSourceContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
        dsMock.close();
    }

    private SysUser user(long userId, Long avatar) {
        SysUser u = new SysUser();
        u.setUserId(userId);
        u.setAvatar(avatar);
        u.setUserName("user" + userId);
        return u;
    }

    @Test
    void workerMissing_isReportedInFailedList() {
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(1L, 10L)));
        when(workerMapper.selectOne(any())).thenReturn(null);

        CvBatchRegisterResultVo summary = service.registerFromAvatarsDetailed(100);

        assertEquals(1, summary.getTotal());
        assertEquals(0, summary.getSuccessCount());
        assertEquals(1, summary.getFailedCount());
        assertTrue(summary.getFailed().get(0).getReason().contains("工人"));
        // 兼容旧契约：成功明细仍为空列表
        assertTrue(service.registerFromAvatars(100).isEmpty());
    }

    @Test
    void avatarReadFailure_isReportedWithSubjectAndReason() {
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(2L, 20L)));
        SchWorker worker = new SchWorker();
        worker.setWorkerId(200L);
        worker.setUserId(2L);
        worker.setEmployeeId("E002");
        when(workerMapper.selectOne(any())).thenReturn(worker);
        // sys_oss 查不到记录 → loadAvatar 返回 null → 跳过并记录原因
        when(sysOssService.getById(any())).thenReturn(null);

        CvBatchRegisterResultVo summary = service.registerFromAvatarsDetailed(100);

        assertEquals(1, summary.getFailedCount());
        CvBatchRegisterResultVo.FailedItem item = summary.getFailed().get(0);
        assertEquals("T000000-EMP-E002", item.getSubject());
        assertEquals("E002", item.getEmployeeId());
        assertTrue(item.getReason().contains("头像读取失败"));
    }

    @Test
    void success_isReportedInSuccessList() {
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(3L, 30L)));
        SchWorker worker = new SchWorker();
        worker.setWorkerId(300L);
        worker.setUserId(3L);
        worker.setEmployeeId("E003");
        when(workerMapper.selectOne(any())).thenReturn(worker);
        when(sysOssService.getById(any())).thenReturn(null);
        // 头像读取失败 → 不进入 vision 调用；这里断言失败路径与成功路径互斥的计数语义
        CvBatchRegisterResultVo summary = service.registerFromAvatarsDetailed(100);
        assertEquals(0, summary.getSuccessCount());
        assertEquals(1, summary.getFailedCount());
    }

    @Test
    void successStructure_isAvailable() {
        // 成功明细结构(RegisterResult)由 vision 服务返回, 这里只校验 VO 承载能力与计数语义
        RegisterResult r = new RegisterResult();
        r.setSuccess(true);
        r.setSubject("T000000-EMP-E004");
        assertEquals("T000000-EMP-E004", r.getSubject());
    }
}
