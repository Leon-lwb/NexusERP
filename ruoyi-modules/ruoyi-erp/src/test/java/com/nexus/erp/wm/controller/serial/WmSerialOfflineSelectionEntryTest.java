package com.nexus.erp.wm.controller.serial;

import com.nexus.erp.wm.controller.app.AppPdaReceiveController;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用当前真实SelectionService重放旧任务，防止契约替身掩盖入口先CAS的缺口。 */
class WmSerialOfflineSelectionEntryTest {
    @Test void realSelectionEntryMustVerifyOriginalCommandAfterAckLossAndLaterMove() throws Exception {
        var s=new WmSerialOfflineCommandReplayTest.Scenario();s.commitLoseAckThenMove();var f=s.f;
        var context=mock(ErpTraceContext.class);when(context.tenant()).thenReturn("000001");
        var repository=spy(new WmSerialInventoryRepository(f.jdbc,mock(WmMaterialStockMapper.class),mock(WmMaterialStockTxMapper.class),context,f.json));
        doNothing().when(repository).requireWrite();
        doAnswer(call->f.request(call.getArgument(0),call.getArgument(1),call.getArgument(2))).when(repository).request(anyString(),anyLong(),anyLong());
        doReturn(f.version).when(repository).sourceHash(any());
        doAnswer(call->f.frozenHistory.get(call.getArgument(0))).when(repository).items(anyLong());
        doAnswer(call->{String sql=call.getArgument(0);if(sql.contains("FROM wm_sn"))return f.repo.row(sql,SerialAdapterFixture.params(call));return call.callRealMethod();}).when(repository).row(anyString(),any(Object[].class));
        AtomicInteger proofChecks=new AtomicInteger();
        doAnswer(call->{
            WmSerialInventoryRepository.Group group=call.getArgument(1);
            assertEquals(99L,group.id());assertEquals("POSTED",group.status());assertEquals(f.frozenHistory.get(99L),call.getArgument(2));
            assertEquals(80L,s.position.get("warehouse"));assertEquals(2,s.physicalPosts.get());proofChecks.incrementAndGet();return null;
        }).when(repository).verifyProof(any(),any(),anyList());
        doNothing().when(repository).requireReplaySource(any());
        var inventory=spy(new WmSerialInventoryService(repository));
        var selections=new WmSerialSelectionService(repository,inventory);
        var store=new WmSerialBusinessStore(f.jdbc,repository,selections);
        var ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(f.connection);when(f.connection.getAutoCommit()).thenReturn(true);
        var transactions=new ProxyFactory(new WmSerialBusinessAdapter(store));transactions.setProxyTargetClass(true);
        transactions.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var adapter=(WmSerialBusinessAdapter)transactions.getProxy();
        var controller=new AspectJProxyFactory(new AppPdaReceiveController(s.receive));controller.setProxyTargetClass(true);controller.addAspect(new WmSerialBusinessAspect(adapter,f.json));
        var mvc=MockMvcBuilders.standaloneSetup((Object)controller.getProxy()).addFilters(new WmSerialPayloadFilter(f.json)).build();
        mvc.perform(post(WmSerialOfflineCommandReplayTest.RECEIVE).contentType(MediaType.APPLICATION_JSON).content(WmSerialOfflineCommandReplayTest.PAYLOAD))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true)).andExpect(jsonPath("$.data.lines[0].postingGroupId").value("99"));
        assertEquals(1,proofChecks.get());verify(repository,never()).request(anyString(),anyLong(),anyLong());verify(repository,never()).sourceHash(any());verify(repository,never()).stocks(anyLong());
        verify(inventory,never()).freeze(any(),anyString(),anyLong(),anyLong(),anyList());verify(inventory,never()).post(any());verify(s.receive,times(1)).finish(10L);
        assertEquals(2,s.physicalPosts.get());assertEquals(80L,s.position.get("warehouse"));verify(f.connection,times(3)).commit();
    }
}
