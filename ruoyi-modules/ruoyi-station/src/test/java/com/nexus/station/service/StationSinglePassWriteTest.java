package com.nexus.station.service;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.pro.domain.*;
import com.nexus.mes.pro.mapper.*;
import com.nexus.mes.pro.service.IProFlowCardService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import com.nexus.mes.pro.service.IProSnService;
import com.nexus.station.domain.MesStationDevice;
import com.nexus.station.domain.MesStationReport;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 单件写入口反例；数据库替身仅用于业务矩阵，事务/SQL/并发由独立内存集成测试验证。
 */
@ExtendWith(MockitoExtension.class)
class StationSinglePassWriteTest {
    @Mock StationReportMapper reportMapper;
    @Mock StationSchWorkerMapper schWorkerMapper;
    @Mock StationDeviceMapper deviceMapper;
    @Mock ProSnMapper proSnMapper;
    @Mock ProFlowCardMapper flowCardMapper;
    @Mock ProFlowCardStepMapper stepMapper;
    @Mock TestRecordMapper testRecordMapper;
    @Mock IProFlowCardService flowCardService;
    @Mock IProSnService proSnService;
    @Mock NcrOrderMapper ncrOrderMapper;
    @Mock StationPassGuardMapper guard;
    @Mock ProWorkOrderStepProgressService progressService;
    @Mock com.nexus.mes.trace.MesSerialReferenceService serialReferences;
    @Mock private com.nexus.mes.trace.MesPreparedWorkOrderPolicies preparedPolicies;
    @InjectMocks StationPassService service;
    MockedStatic<TenantHelper> tenant;
    ProSn sn;
    ProFlowCard card;
    ProFlowCardStep current;
    MesStationDevice device;
    StationPassReportDto dto;
    final StationPassService.OperatorInfo operator = new StationPassService.OperatorInfo(105L,"EMP-1","操作员");

    @BeforeEach
    void setup() {
        lenient().when(serialReferences.submissionStatus(any())).thenReturn(java.util.Map.of("submissionState","SERVER_COMMITTED","handoffStatus","NONE"));
        lenient().when(progressService.lockForSn(anyString())).thenAnswer(i -> sn.getMesWoId());
        lenient().when(progressService.lockForCard(anyLong())).thenAnswer(i -> flowCardMapper.selectById((Long)i.getArgument(0)));
        for (Class<?> entity : List.of(ProSn.class,ProFlowCard.class,ProFlowCardStep.class,
            MesStationReport.class,MesStationDevice.class,TestRecord.class,NcrOrder.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"unit"),entity);
        }
        tenant=mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        DynamicDataSourceContextHolder.push("mes");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        sn=new ProSn(); sn.setSnId(1L); sn.setIdentityId(10001L); sn.setTenantId("000000"); sn.setDelFlag("0");
        sn.setSnCode("SN-1"); sn.setCardId(10L); sn.setMesWoId(20L); sn.setCurrentStepSeq(30); sn.setSnStatus("IN_PRODUCTION");
        card=new ProFlowCard(); card.setCardId(10L); card.setTenantId("000000"); card.setDelFlag("0"); card.setCardStatus("IN_PROGRESS"); card.setMesWoId(20L);
        current=step(30,"ASM","IN_PROGRESS");
        device=new MesStationDevice(); device.setDeviceId(5L); device.setTenantId("000000"); device.setDelFlag("0");
        device.setStationCode("STN|JZ|L1|ASM|GENERAL"); device.setProcessCode("ASM");
        dto=new StationPassReportDto(); dto.setSnCode("SN-1"); dto.setStationCode(device.getStationCode()); dto.setResult("PASS"); dto.setClientTs(1787295823000L);
        lenient().when(serialReferences.resolve("SN-1")).thenAnswer(i ->
            new com.nexus.mes.trace.MesSerialReferenceService.Resolved("000000",10001L,1L,"SN-1","SN-1",sn.getMaterialId(),sn));
        lenient().when(proSnMapper.selectOne(any())).thenReturn(sn);
        lenient().when(flowCardMapper.selectById(anyLong())).thenReturn(card);
        lenient().when(stepMapper.selectOne(any())).thenReturn(current);
        lenient().when(stepMapper.selectList(any())).thenReturn(List.of(current, step(40,"PKG","PENDING")));
        lenient().when(stepMapper.selectById(anyLong())).thenReturn(current);
        lenient().when(deviceMapper.selectOne(any())).thenReturn(device);
        lenient().when(reportMapper.selectCount(any())).thenReturn(0L);
        lenient().when(ncrOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(reportMapper.insert(any(MesStationReport.class))).thenReturn(1);
        lenient().when(stepMapper.update(any(),any())).thenReturn(1);
        lenient().when(proSnMapper.update(any(),any())).thenReturn(1);
        // 延续 RED 阶段事实矩阵；仅将来源替身适配到新的固定锁定读接口。
        lenient().when(guard.lockSn(anyString(),anyString())).thenAnswer(i -> List.of(sn));
        lenient().when(guard.lockCard(anyString(),anyLong())).thenAnswer(i -> card);
        lenient().when(guard.lockSteps(anyString(),anyLong())).thenAnswer(i -> stepMapper.selectList(null));
        lenient().when(guard.lockDevices(anyString(),anyString())).thenAnswer(i -> {
            var d=deviceMapper.selectOne(null); return d==null?List.of():List.of(d);
        });
        lenient().when(guard.lockReports(anyString(),anyString())).thenAnswer(i -> {
            var existing=reportMapper.selectByClientKey("key");
            if(existing!=null) {
                var stored=new StationPassGuardMapper.StoredReport();
                org.springframework.beans.BeanUtils.copyProperties(existing,stored); stored.setDelFlag("0");
                return List.of(stored);
            }
            if(reportMapper.selectCount(null)>0) {
                var r=new StationPassGuardMapper.StoredReport(); org.springframework.beans.BeanUtils.copyProperties(replay(),r);
                r.setDelFlag("0"); r.setClientKey("earlier-key"); r.setResult("PASS"); return List.of(r);
            }
            return List.of();
        });
        lenient().when(guard.lockTests(anyString(),anyString())).thenAnswer(i -> {
            var t=testRecordMapper.selectOne(null); if(t==null)return List.of();
            t.setTenantId("000000"); t.setSnCode("SN-1"); t.setDelFlag("0"); return List.of(t);
        });
        lenient().when(guard.lockNcrs(anyString(),anyLong())).thenAnswer(i -> {
            if(ncrOrderMapper.selectCount(null)==0)return List.of();
            var n=new NcrOrder(); n.setTenantId("000000"); n.setCardId(10L); n.setDelFlag("0"); n.setStatus("OPEN"); return List.of(n);
        });
    }
    @AfterEach void cleanup() {
        tenant.close(); DynamicDataSourceContextHolder.clear(); TransactionSynchronizationManager.clear();
    }
    ProFlowCardStep step(int seq,String process,String status) {
        var s=new ProFlowCardStep(); s.setStepRecordId((long)seq); s.setTenantId("000000"); s.setDelFlag("0"); s.setCardId(10L);
        s.setMesWoId(20L); s.setSequenceNum(seq); s.setProcessCode(process); s.setProcessName(process); s.setStepStatus(status); s.setPlannedQty(BigDecimal.TEN); s.setGoodQty(BigDecimal.ZERO); s.setDefectQty(BigDecimal.ZERO); s.setScrapQty(BigDecimal.ZERO);
        return s;
    }
    void rejectWithoutWrites() {
        assertThrows(ServiceException.class, () -> service.reportSingle(dto,operator));
        verify(reportMapper,never()).insert(any(MesStationReport.class));
        verify(stepMapper,never()).update(any(),any());
        verify(proSnMapper,never()).update(any(),any());
        verifyNoInteractions(flowCardService,proSnService);
    }
    @Test void unfinishedPreviousStepCannotBeBypassed() {
        lenient().when(stepMapper.selectList(any())).thenReturn(List.of(step(10,"WELD","IN_PROGRESS"),current));
        rejectWithoutWrites();
    }
    @Test void wrongRegisteredProcessCannotBeBypassed() { device.setProcessCode("WELD"); rejectWithoutWrites(); }
    @Test void unregisteredEncodedStationCannotAuthorizeWrite() { lenient().when(deviceMapper.selectOne(any())).thenReturn(null); rejectWithoutWrites(); }
    @Test void latestFailedTestCannotBeBypassed() {
        var test=new TestRecord(); test.setResult("FAIL"); test.setTestNo("TEST-1");
        lenient().when(testRecordMapper.selectOne(any())).thenReturn(test); rejectWithoutWrites();
    }
    @Test void openNcrCannotBeBypassed() { lenient().when(ncrOrderMapper.selectCount(any())).thenReturn(1L); rejectWithoutWrites(); }
    @Test void otherTimestampCannotBypassPreviousPass() { lenient().when(reportMapper.selectCount(any())).thenReturn(1L); rejectWithoutWrites(); }
    @Test void sameKeyDifferentResultIsNotSuccessfulReplay() {
        var existing=replay(); existing.setResult("FAIL"); lenient().when(reportMapper.selectByClientKey(any())).thenReturn(existing); rejectWithoutWrites();
    }
    @Test void sameKeyDifferentOperatorIsNotSuccessfulReplay() {
        var existing=replay(); dto.setOperatorId(999L); lenient().when(reportMapper.selectByClientKey(any())).thenReturn(existing); rejectWithoutWrites();
    }
    @Test void sameKeyDifferentDefectsIsNotSuccessfulReplay() {
        dto.setResult("FAIL"); dto.setDefectCodes(List.of("D1"));
        var existing=replay(); existing.setDefectCodes("D2"); lenient().when(reportMapper.selectByClientKey(any())).thenReturn(existing); rejectWithoutWrites();
    }
    @Test void missingTenantCannotAuthorizeWrite() { tenant.when(TenantHelper::getTenantId).thenReturn(null); rejectWithoutWrites(); }
    @Test void wrongSourceTenantCannotAuthorizeWrite() { sn.setTenantId("OTHER"); rejectWithoutWrites(); }
    @Test void changedIdentityCannotReuseResolvedLocalReference() { sn.setIdentityId(20002L); rejectWithoutWrites(); }
    @Test void noPhysicalTransactionCannotAuthorizeWrite() { TransactionSynchronizationManager.setActualTransactionActive(false); rejectWithoutWrites(); }
    @Test void wrongDatasourceCannotAuthorizeWrite() { DynamicDataSourceContextHolder.push("erp"); rejectWithoutWrites(); }
    @Test void readOnlyTransactionCannotAuthorizeWrite() { TransactionSynchronizationManager.setCurrentTransactionReadOnly(true); rejectWithoutWrites(); }
    @Test void terminalCardCannotAuthorizeWrite() { card.setCardStatus("CLOSED"); rejectWithoutWrites(); }
    @Test void requestCannotOverflowStoredClientKey() { dto.setSnCode("S".repeat(65)); rejectWithoutWrites(); }
    @Test void sameCommittedPayloadReplaysWithoutAdvancingAgain() {
        lenient().when(reportMapper.selectByClientKey(any())).thenReturn(replay()); sn.setSnStatus("COMPLETED");
        assertEquals("SERVER_COMMITTED",service.reportSingle(dto,operator).get("submissionState"));
        verify(reportMapper,never()).insert(any(MesStationReport.class)); verify(proSnMapper,never()).update(any(),any());
    }

    @Test void malformedNcrStatusCannotPretendToBeClosed() {
        var n=new NcrOrder(); n.setTenantId("000000"); n.setCardId(10L); n.setDelFlag("0"); n.setStatus("UNKNOWN");
        when(guard.lockNcrs(anyString(),anyLong())).thenReturn(List.of(n)); rejectWithoutWrites();
    }
    @Test void malformedLatestTestCannotPretendToPass() {
        var t=new TestRecord(); t.setTenantId("000000"); t.setSnCode("SN-1"); t.setDelFlag("0"); t.setResult("UNKNOWN");
        when(guard.lockTests(anyString(),anyString())).thenReturn(List.of(t)); rejectWithoutWrites();
    }
    @Test void conflictingDeviceBindingsCannotAuthorizeWrite() {
        var other=new MesStationDevice(); org.springframework.beans.BeanUtils.copyProperties(device,other); other.setProcessCode("WELD");
        when(guard.lockDevices(anyString(),anyString())).thenReturn(List.of(device,other)); rejectWithoutWrites();
    }
    @Test void absentCurrentCounterCannotAuthorizeWrite() { current.setGoodQty(null); rejectWithoutWrites(); }
    @Test void duplicateSequenceCannotAuthorizeWrite() {
        when(guard.lockSteps(anyString(),anyLong())).thenReturn(List.of(current,step(30,"PKG","PENDING"))); rejectWithoutWrites();
    }
    @Test void failedQuantityWriteDoesNotBecomeSuccessfulReport() {
        when(stepMapper.update(any(),any())).thenReturn(0);
        assertThrows(ServiceException.class,()->service.reportSingle(dto,operator));
        verify(proSnMapper,never()).update(any(),any());
    }
    MesStationReport replay() {
        var r=new MesStationReport(); r.setTenantId("000000"); r.setReportType("SINGLE"); r.setSnCode("SN-1"); r.setStationCode(device.getStationCode());
        r.setClientKey("SINGLE|SN-1|"+device.getStationCode()+"|"+dto.getClientTs()); r.setClientTs(dto.getClientTs());
        r.setResult(dto.getResult()); r.setOperatorId(operator.userId()); r.setOperatorCode(operator.code()); r.setDefectCodes(null);
        r.setGoodQty("PASS".equals(dto.getResult())?BigDecimal.ONE:BigDecimal.ZERO); r.setDefectQty("FAIL".equals(dto.getResult())?BigDecimal.ONE:BigDecimal.ZERO);
        r.setMesWoId(20L); r.setCardId(10L); r.setStepRecordId(30L);
        r.setRequestFingerprint(StationSinglePassFingerprint.of("000000","SN-1",device.getStationCode(),dto.getClientTs(),dto.getResult(),
            dto.getOperatorId(),dto.getOperatorCode(),dto.getDefectCodes()==null?null:String.join(",",dto.getDefectCodes()))); return r;
    }
}
