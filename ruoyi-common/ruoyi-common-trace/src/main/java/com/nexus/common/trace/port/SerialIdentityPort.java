package com.nexus.common.trace.port;
import com.nexus.common.trace.*;
/** 正式身份注册端口，由ERP实现，跨库编排不得持有MES行锁调用。 */
public interface SerialIdentityPort {
    SerialReservation reserve(String tenantId,SerialReservationCommand command);
    void confirm(String tenantId,String commandId,String fingerprint);
    SerialIdentity find(String tenantId,Long identityId);
}
