package com.nexus.common.trace.port;
import com.nexus.common.trace.MaterialTraceEvent;
/** Verifies a committed source outbox before opening any target transaction. A payload hash is not authority. */
public interface TraceSourceVerificationPort {
    String sourceModule();
    void verify(MaterialTraceEvent event);
}
