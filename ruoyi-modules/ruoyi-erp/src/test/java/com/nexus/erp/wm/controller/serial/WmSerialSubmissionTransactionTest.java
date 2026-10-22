package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.domain.R;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WmSerialSubmissionTransactionTest {
    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @ParameterizedTest @ValueSource(strings = {"transfer", "stocktaking"})
    void approvalStartsOnlyAfterSerialValidationTransactionHasReleasedHeadLock(String path) throws Throwable {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:submission_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=150");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE business_head(id BIGINT PRIMARY KEY, status VARCHAR(30))");
        jdbc.update("INSERT INTO business_head VALUES(11,'DRAFT')");
        WmSerialBusinessStore store = mock(WmSerialBusinessStore.class);
        when(store.head(any(), eq(11L))).thenAnswer(i -> {
            jdbc.queryForObject("SELECT status FROM business_head WHERE id=11 FOR UPDATE", String.class);
            return Map.of("status", "DRAFT");
        });
        when(store.sourceId(any(), eq(11L))).thenReturn(11L);
        when(store.lineIds(any(), eq(11L))).thenReturn(List.of());
        when(store.submission(any(), any())).thenReturn(Map.of());
        ProxyFactory factory = new ProxyFactory(new WmSerialBusinessAdapter(store));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        var adapter = (WmSerialBusinessAdapter)factory.getProxy();
        ObjectMapper json = new ObjectMapper();
        var aspect = new WmSerialBusinessAspect(adapter, json);
        String uri = "/erp/wm/" + path + (path.equals("transfer") ? "/11/submit" : "/submit/11");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setAttribute(WmSerialPayloadFilter.BODY, json.createObjectNode());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint join = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class); when(signature.getName()).thenReturn("submit"); when(join.getSignature()).thenReturn(signature);
        when(join.proceed()).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "同步流程启动不能被仍持有ERP单头锁的事务包围");
            TransactionTemplate callback = new TransactionTemplate(new DataSourceTransactionManager(source));
            callback.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            callback.executeWithoutResult(s -> jdbc.update("UPDATE business_head SET status='APPROVING' WHERE id=11"));
            return R.ok();
        });
        try {
            assertInstanceOf(R.class, aspect.controller(join));
            assertEquals("APPROVING", jdbc.queryForObject("SELECT status FROM business_head WHERE id=11", String.class));
            verify(store).head(any(), eq(11L));
            verify(join).proceed();
        } finally { try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("SHUTDOWN"); } }
    }
}
