package com.nexus.erp.wm.controller.serial;

import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.pda.PdaIdempotentAspect;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.wm.controller.app.AppPdaIssueController;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.service.IPdaIssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.*;
import org.springframework.http.MediaType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真正的原PDA幂等切面必须观察到适配事务已经提交；Redis仅为内存模拟。 */
class WmSerialPdaIdempotencyTest {
    @Configuration @EnableAspectJAutoProxy(proxyTargetClass=true)
    static class Beans {
        @Bean static WmSerialAdvisorOrder serialOrder(){return new WmSerialAdvisorOrder();}
        @Bean SerialAdapterFixture fixture() throws Exception{return new SerialAdapterFixture();}
        @Bean static SpringUtils springUtils(){return new SpringUtils();}
        @Bean RedissonClient redisson(){return mock(RedissonClient.class);}
        @Bean WmSerialBusinessAspect wmSerialBusinessAspect(SerialAdapterFixture f){return new WmSerialBusinessAspect(f.adapter,f.json);}
        @Bean PdaIdempotentAspect pdaIdempotentAspect(){return new PdaIdempotentAspect();}
        @Bean IPdaIssueService pda(SerialAdapterFixture f) {
            var service=mock(IPdaIssueService.class);
            when(service.pick(eq(10L),any())).thenAnswer(call->{f.version="after:pick";f.inventory.post(f.request("WM_PRODUCTION_ISSUE",10L,11L));return PdaActionResultVo.ok();});return service;
        }
        @Bean AppPdaIssueController controller(IPdaIssueService pda){return new AppPdaIssueController(pda);}
    }
    @Test void originalPdaReplayHappensAfterCommitAndDoesNotRepeatStaleCas() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(Beans.class);
            var cache=mockStatic(RedisUtils.class);var login=mockStatic(LoginHelper.class)) {
            var f=context.getBean(SerialAdapterFixture.class);AtomicInteger calls=new AtomicInteger();
            login.when(LoginHelper::getTenantId).thenReturn("000001");login.when(LoginHelper::getUserId).thenReturn(1L);
            cache.when(()->RedisUtils.setObjectIfAbsent(anyString(),anyString(),any(Duration.class))).thenAnswer(call->calls.getAndIncrement()==0);
            cache.when(()->RedisUtils.getCacheObject(anyString())).thenReturn("DONE");
            cache.when(()->RedisUtils.setCacheObject(anyString(),eq("DONE"),any(Duration.class))).thenAnswer(call->{
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"幂等DONE不得早于事务提交");verify(f.connection).commit();return null;
            });
            var mvc=MockMvcBuilders.standaloneSetup(context.getBean(AppPdaIssueController.class)).addFilters(new WmSerialPayloadFilter(f.json)).build();
            String body="{\"lineId\":\"11\",\"qty\":\"1\",\"locationCode\":\"A\",\"commandId\":\"same-pick\",\"detailVersion\":\"opaque:事实/一\",\"snItems\":[{\"identityId\":\"101\"}]}";
            mvc.perform(post("/app/pda/wm/issue/10/line/pick").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.ok").value(true));
            mvc.perform(post("/app/pda/wm/issue/10/line/pick").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true));
            verify(context.getBean(IPdaIssueService.class),times(1)).pick(eq(10L),any());verify(f.selections,times(1)).freeze(any());
        }
    }
    @Test void newSnAndCommandFieldsParticipateInOldPdaIdempotency() throws Exception {
        ObjectMapper json=new ObjectMapper();var filter=new WmSerialPayloadFilter(json);
        String a=key(filter,"{\"commandId\":\"c\",\"snItems\":[{\"identityId\":\"101\"},{\"identityId\":\"102\"}]}");
        String reordered=key(filter,"{\"snItems\":[{\"identityId\":\"102\"},{\"identityId\":\"101\"}],\"commandId\":\"c\"}");
        String otherSn=key(filter,"{\"commandId\":\"c\",\"snItems\":[{\"identityId\":\"103\"}]}");
        assertEquals(a,reordered);assertNotEquals(a,otherSn);
    }
    @Test void oldQuantityRequestKeepsOriginalExplicitIdempotencyHeader() throws Exception {
        var request=new MockHttpServletRequest("POST","/app/pda/wm/issue/10/finish");request.addHeader("X-Idempotent-Key","old-quantity-key");request.setContentType(MediaType.APPLICATION_JSON_VALUE);request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new WmSerialPayloadFilter(new ObjectMapper()).doFilter(request,new MockHttpServletResponse(),(r,s)->assertEquals("old-quantity-key",((jakarta.servlet.http.HttpServletRequest)r).getHeader("X-Idempotent-Key")));
    }
    static String key(WmSerialPayloadFilter filter,String body) throws Exception {
        var request=new MockHttpServletRequest("POST","/app/pda/wm/issue/10/finish");request.setContentType(MediaType.APPLICATION_JSON_VALUE);request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));String[] key={null};
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->key[0]=((jakarta.servlet.http.HttpServletRequest)r).getHeader("X-Idempotent-Key"));return key[0];
    }
}
