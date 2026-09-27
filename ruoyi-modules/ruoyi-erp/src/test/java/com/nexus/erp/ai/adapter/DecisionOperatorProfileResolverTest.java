package com.nexus.erp.ai.adapter;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.domain.dto.UserDTO;
import com.nexus.common.core.service.UserService;
import com.nexus.common.core.service.DeptService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("dev") class DecisionOperatorProfileResolverTest {
    @Test void usesMasterAndSuspendsErpTransactionBeforeResolvingTenantScopedUserAndDepartment() throws Exception {
        var method=DecisionOperatorProfileResolver.class.getMethod("resolve",DecisionActor.class);
        assertEquals("master",method.getAnnotation(DS.class).value());
        assertEquals(Propagation.NOT_SUPPORTED,method.getAnnotation(Transactional.class).propagation());
        var users=mock(UserService.class);var depts=mock(DeptService.class);
        UserDTO user=new UserDTO();user.setUserId(1L);user.setUserName("admin");user.setDeptId(103L);user.setStatus("0");
        when(users.selectListByIds(List.of(1L))).thenReturn(List.of(user));
        when(depts.selectDeptNamesByIds(List.of(103L))).thenReturn(Map.of(103L,"研发部门"));
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(()->TenantHelper.dynamic(eq("000000"),any(Supplier.class))).thenAnswer(c->((Supplier<?>)c.getArgument(1)).get());
            var p=new DecisionOperatorProfileResolver(users,depts).resolve(new DecisionActor("000000",1L,"admin",Set.of()));
            assertEquals(103L,p.deptId());assertEquals("研发部门",p.deptName());assertEquals("admin",p.userName());
        }
    }
    @Test void missingRevokedOrChangedIdentityAndUnresolvableDepartmentAreRejected() {
        var users=mock(UserService.class);var depts=mock(DeptService.class);var resolver=new DecisionOperatorProfileResolver(users,depts);
        try(var tenant=mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(()->TenantHelper.dynamic(anyString(),any(Supplier.class))).thenAnswer(c->((Supplier<?>)c.getArgument(1)).get());
            var actor=new DecisionActor("000000",1L,"admin",Set.of());
            when(users.selectListByIds(anyList())).thenReturn(List.of());assertThrows(ServiceException.class,()->resolver.resolve(actor));
            UserDTO wrong=new UserDTO();wrong.setUserId(1L);wrong.setUserName("other");wrong.setStatus("0");
            when(users.selectListByIds(anyList())).thenReturn(List.of(wrong));assertThrows(ServiceException.class,()->resolver.resolve(actor));
            wrong.setUserName("admin");wrong.setStatus("1");assertThrows(ServiceException.class,()->resolver.resolve(actor));
            wrong.setStatus("0");wrong.setDeptId(103L);when(depts.selectDeptNamesByIds(anyList())).thenReturn(Map.of());
            assertThrows(ServiceException.class,()->resolver.resolve(actor));
        }
    }
}
