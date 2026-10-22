package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.mapper.WmCertifiedWarehouseReadMapper;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingAuthorizer;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.*;
import java.util.*;
import java.util.Locale;

/** 隔离真实只读 Service/Mapper/Controller 合同；不连接数据库、Redis或真正写 Service。 */
class WmCertifiedWarehouseReadTest {
    /** MySQL5.7实际1064已只读复现：日期常量带WHERE时必须有DUAL，不是H2/替身验收。 */
    @Test void databaseClockWithTenantPredicateUsesMysqlDualRatherThanFromlessWhere() throws Exception {
        Method m = WmCertifiedWarehouseReadMapper.class.getMethod("databaseDate", String.class);
        String sql =
                String.join(" ", m.getAnnotation(Select.class).value()).toUpperCase(Locale.ROOT);
        assertTrue(sql.contains("FROM DUAL WHERE"));
        assertTrue(sql.contains("#{TENANT}=#{TENANT}"));
        assertFalse(sql.contains("FOR UPDATE"));
        assertFalse(m.getAnnotation(Options.class).useCache());
    }

    private static Class<?> type(String name) {try{return Class.forName("com.nexus.erp.wm."+name);}catch(ClassNotFoundException missing){return fail("缺少独立仓储只读合同："+name);}}
    private static Object call(Object target,String name,Object... args) {
        var method=Arrays.stream(target instanceof Class<?> c?c.getMethods():target.getClass().getMethods()).filter(m->m.getName().equals(name)&&m.getParameterCount()==args.length).findFirst().orElseThrow();
        try{return method.invoke(target instanceof Class<?>?null:target,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;if(failure.getCause() instanceof Error error)throw error;throw new AssertionError(failure.getCause());}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static Object service(Object mapper,WmCertifiedReceiptPostingAuthorizer auth) {
        try {var constructor=type("service.WmCertifiedWarehouseReadService").getConstructors()[0];Object[] args=new Object[constructor.getParameterCount()];args[0]=mapper;args[1]=auth;for(int i=2;i<args.length;i++)args[i]=mock(constructor.getParameterTypes()[i]);return constructor.newInstance(args);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static Map<String,Object> head(Object version) {var row=new LinkedHashMap<String,Object>();row.put("tenant_id","000001");row.put("order_id",7L);row.put("version",version);row.put("del_flag","0");return row;}
    private static void identity(MockedStatic<TenantHelper> tenant,MockedStatic<LoginHelper> login) {
        tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn("000001");login.when(LoginHelper::getTenantId).thenReturn("000001");login.when(LoginHelper::getUserId).thenReturn(101L);login.when(LoginHelper::getUsername).thenReturn("reader");login.when(LoginHelper::getDeptId).thenReturn(null);
    }
    @Test void controllersAreQueryOnlyAndDomainsHaveDifferentPermissions() {
        var controller=type("controller.WmCertifiedWarehouseReadController");long count=0;
        for(var method:controller.getDeclaredMethods()){var get=method.getAnnotation(GetMapping.class);assertNotNull(get);String permission=method.getName().startsWith("arrival")?"wm:arrivalnotice:query":"wm:receive:query";assertArrayEquals(new String[]{permission},method.getAnnotation(SaCheckPermission.class).value());count++;}assertEquals(6,count);
    }
    @Test void mapperSelectsAreTenantExplicitUncachedAndNeverLockOrWrite() {
        for(var method:type("mapper.WmCertifiedWarehouseReadMapper").getDeclaredMethods()) {
            var select=method.getAnnotation(Select.class);assertNotNull(select);String sql=String.join(" ",select.value()).toUpperCase(Locale.ROOT);assertTrue(sql.startsWith("SELECT "));assertTrue(sql.contains("#{TENANT}"));assertFalse(sql.contains("FOR UPDATE"));assertFalse(sql.contains("LOCK IN SHARE"));assertFalse(sql.matches(".*\\b(INSERT|UPDATE|DELETE|REPLACE|ALTER)\\b.*"));var options=method.getAnnotation(Options.class);assertNotNull(options);assertFalse(options.useCache());assertEquals(Options.FlushCachePolicy.TRUE,options.flushCache());
        }
    }
    @Test void actualPublicReadMethodsRouteErpAndWorkflowRoutesMaster() {
        for(var method:type("service.WmCertifiedWarehouseReadService").getDeclaredMethods())if(Modifier.isPublic(method.getModifiers()))assertEquals(method.getName().equals("withWorkflow")?"master":"erp",method.getAnnotation(DS.class).value());
    }
    @Test void receiveReaderUsesExistingQueryActorNotWriteActorAndAllowsUnknownDept() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));when(call(mapper,"databaseDate","000001")).thenReturn("2026-10-09");var auth=mock(WmCertifiedReceiptPostingAuthorizer.class);when(auth.requireQuery()).thenReturn(new DecisionActor("000001",101L,"reader",Set.of("wm:receive:query")));
            var ctx=call(service(mapper,auth),"receiveContext");assertEquals("000001",call(ctx,"tenantId"));assertEquals("101",call(ctx,"userId"));assertEquals("reader",call(ctx,"username"));assertNull(call(ctx,"deptId"));assertEquals(false,call(ctx,"createAllowed"));verify(auth).requireQuery();verifyNoMoreInteractions(auth);assertEquals(1,mockingDetails(mapper).getInvocations().size());
        }
    }
    @Test void arrivalQueryDoesNotBorrowReceiveQueryOrWriterAndMismatchTenantIsRejected() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));when(call(mapper,"databaseDate","000001")).thenReturn("2026-10-09");var auth=mock(WmCertifiedReceiptPostingAuthorizer.class);var svc=service(mapper,auth);assertEquals("arrivalnotice",call(call(svc,"arrivalContext"),"domain"));stp.verify(()->StpUtil.checkPermission("wm:arrivalnotice:query"));verifyNoInteractions(auth);
            login.when(LoginHelper::getTenantId).thenReturn("000002");assertThrows(RuntimeException.class,()->call(svc,"arrivalContext"));
        }
    }
    @Test void nativeNullVersionIsKeptUnknownAndNeverCoalescedToLegacyZero() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));when(call(mapper,"order","000001",7L)).thenReturn(head(null));when(call(mapper,"orderLines","000001",7L)).thenReturn(List.of());when(call(mapper,"orderSources","000001",7L)).thenReturn(List.of());when(call(mapper,"approvals","000001",7L)).thenReturn(List.of());
            var auth=mock(WmCertifiedReceiptPostingAuthorizer.class);when(auth.requireQuery()).thenReturn(new DecisionActor("000001",101L,"reader",Set.of()));var detail=call(service(mapper,auth),"receiveOrder",7L);assertEquals("UNKNOWN",call(detail,"protection"));assertEquals(false,call(detail,"sourceComplete"));assertNull(((Map<?,?>)call(detail,"order")).get("version"));
            assertEquals(4,mockingDetails(mapper).getInvocations().size());
        }
    }
    @Test void noticeQueryDoesNotReadReceiptTablesEvenForCertifiedProtectedFact() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));when(call(mapper,"notice","000001",7L)).thenReturn(Map.of("tenant_id","000001","notice_id",7L,"del_flag","0","attr1","CERTIFIED_LABEL_ONLY","status","DRAFT"));when(call(mapper,"noticeLines","000001",7L)).thenReturn(List.of());when(call(mapper,"noticeCreations","000001",7L)).thenReturn(List.of());var detail=call(service(mapper,mock(WmCertifiedReceiptPostingAuthorizer.class)),"arrivalDetail",7L);assertEquals("CERTIFIED",call(detail,"protection"));assertEquals(false,call(detail,"sourceComplete"));assertEquals("UNKNOWN",call(detail,"flowState"));assertTrue(mockingDetails(mapper).getInvocations().stream().noneMatch(i->i.getMethod().getName().startsWith("receive")||i.getMethod().getName().equals("birthCount")||i.getMethod().getName().equals("posting")));
        }
    }
    @Test void foreignTenantNativeRowsAreRejectedBeforeReturningData() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));var foreign=head(0);foreign.put("tenant_id","000002");when(call(mapper,"order","000001",7L)).thenReturn(foreign);assertThrows(RuntimeException.class,()->call(service(mapper,mock(WmCertifiedReceiptPostingAuthorizer.class)),"arrivalOrder",7L));assertEquals(1,mockingDetails(mapper).getInvocations().size());
        }
    }
    @Test void receiptQueryDoesNotReadNoticeTablesAndOldBirthCannotBecomeCompleteSource() {
        try(var tenant=mockStatic(TenantHelper.class);var login=mockStatic(LoginHelper.class);var stp=mockStatic(StpUtil.class)) {
            identity(tenant,login);var mapper=mock(type("mapper.WmCertifiedWarehouseReadMapper"));when(call(mapper,"receive","000001",7L)).thenReturn(Map.of("tenant_id","000001","receive_id",7L,"del_flag","0","status","APPROVED"));
            for(String name:List.of("receiveLines","receiveDetails","receiveCreations","iqcEvidence"))when(call(mapper,name,"000001",7L)).thenReturn(List.of());
            when(call(mapper,"birthCount","000001",7L)).thenReturn(1L);when(call(mapper,"births","000001",7L)).thenReturn(List.of(Map.of("tenant_id","000001","birth_id",8L,"receive_id",7L)));
            var auth=mock(WmCertifiedReceiptPostingAuthorizer.class);when(auth.requireQuery()).thenReturn(new DecisionActor("000001",101L,"reader",Set.of()));var detail=call(service(mapper,auth),"receiveDetail",7L);assertEquals("CERTIFIED",call(detail,"protection"));assertEquals(false,call(detail,"sourceComplete"));assertEquals(List.of(),call(detail,"iqcEvidence"));
            assertTrue(mockingDetails(mapper).getInvocations().stream().noneMatch(i->i.getMethod().getName().startsWith("notice")||i.getMethod().getName().equals("arrivalDetail")));
        }
    }
    @Test void nativeWarehouseJsonPropertyMatchesActualClientContract() {
        var detail = type("service.WmCertifiedWarehouseReadService$Detail");
        try {
            assertEquals(
                    "native",
                    detail.getMethod("nativeDocument").getAnnotation(JsonProperty.class).value());
        } catch (NoSuchMethodException missing) {
            fail("缺少完整原生仓储头契约");}
    }

}
