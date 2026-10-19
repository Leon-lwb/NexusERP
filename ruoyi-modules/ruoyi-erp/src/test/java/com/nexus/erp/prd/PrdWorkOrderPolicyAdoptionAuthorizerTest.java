package com.nexus.erp.prd;
import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.prd.service.PrdWorkOrderPolicyAdoptionAuthorizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PrdWorkOrderPolicyAdoptionAuthorizerTest {
 @Test void requiredPermissionAndActorComeFromAuthenticatedContext() {
  try(var permissions=mockStatic(StpUtil.class);var login=mockStatic(LoginHelper.class)) {
   login.when(LoginHelper::getUserId).thenReturn(22L);assertEquals(22L,new PrdWorkOrderPolicyAdoptionAuthorizer().requireApprover());
   permissions.verify(()->StpUtil.checkPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION));
  }
 }
 @Test void aDeniedPermissionCannotBeReplacedByAnActorId() {
  try(var permissions=mockStatic(StpUtil.class);var login=mockStatic(LoginHelper.class)) {
   login.when(LoginHelper::getUserId).thenReturn(22L);permissions.when(()->StpUtil.checkPermission(PrdWorkOrderPolicyAdoptionAuthorizer.PERMISSION)).thenThrow(new IllegalStateException("denied"));
   assertThrows(RuntimeException.class,()->new PrdWorkOrderPolicyAdoptionAuthorizer().requireApprover());
  }
 }
 @Test void anUnauthenticatedOrUnknownActorCannotApplyAnApprovedRecord() {
  try(var permissions=mockStatic(StpUtil.class);var login=mockStatic(LoginHelper.class)) {
   assertThrows(RuntimeException.class,()->new PrdWorkOrderPolicyAdoptionAuthorizer().requireApprover());
  }
 }
}
