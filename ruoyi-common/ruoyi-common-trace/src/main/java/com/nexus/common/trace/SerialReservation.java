package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
/** 可恢复的发号结果；已预留号码永久占用，业务取消不会回收。 */
public record SerialReservation(@JsonFormat(shape=JsonFormat.Shape.STRING) Long reservationId,String commandId,String fingerprint,List<SerialIdentity> identities,String status) {
    public SerialReservation { identities=List.copyOf(identities); }
}
