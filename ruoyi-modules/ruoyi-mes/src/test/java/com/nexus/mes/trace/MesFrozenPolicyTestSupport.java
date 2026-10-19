package com.nexus.mes.trace;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.*;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
final class MesFrozenPolicyTestSupport {
    static MesPreparedWorkOrderPolicies prepared(MesTraceBusinessTest f,TrackingPolicy frozen,AtomicBoolean unknown,AtomicInteger reads){
        WorkOrderTrackingPolicyPort bank=(tenant,wo,material)->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());reads.incrementAndGet();
            if(unknown.get())return WorkOrderTrackingPolicySnapshot.unknown(tenant,wo,material,"legacy release unknown");
            var time=Instant.parse("2026-10-18T00:00:00Z");
            return new WorkOrderTrackingPolicySnapshot(tenant,wo,material,WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,
                frozen,1,"a".repeat(64),"b".repeat(64),"c".repeat(64),"ERP_RELEASE:"+wo,time,time,null,null);
        };
        var factory=new ProxyFactory(new MesWorkOrderTrackingPolicyReader(f.jdbc,f.context,bank));factory.setProxyTargetClass(true);
        factory.addAdvisor(new DynamicDataSourceAnnotationAdvisor(new DynamicDataSourceAnnotationInterceptor(true,new DsSpelExpressionProcessor()),DS.class));
        var tx=new TransactionInterceptor();tx.setTransactionManager(f.tx.getTransactionManager());tx.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());factory.addAdvice(tx);
        var prepared=new MesPreparedWorkOrderPolicies(f.jdbc,f.context,(MesWorkOrderTrackingPolicyReader)factory.getProxy());ReflectionTestUtils.setField(prepared,"traceRelayEnabled",true);return prepared;
    }
}
