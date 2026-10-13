package com.nexus.common.report;
import java.util.concurrent.atomic.AtomicReference;
public final class ReportCancellation {
    private final AtomicReference<Runnable> action=new AtomicReference<>();private volatile String reason;
    public boolean cancelled(){return reason!=null;}
    public String reason(){return reason;}
    public void attach(Runnable callback){action.set(callback);if(cancelled())invoke(callback);}
    public void detach(){action.set(null);}
    public void cancel(String code){reason=code;Runnable callback=action.get();if(callback!=null)invoke(callback);}
    public void check(){if(cancelled())throw new DatasetAccessException(reason,"报表执行已停止");}
    private static void invoke(Runnable callback){try{callback.run();}catch(RuntimeException ignored){/* A closed statement is already cancelled. */}}
}
