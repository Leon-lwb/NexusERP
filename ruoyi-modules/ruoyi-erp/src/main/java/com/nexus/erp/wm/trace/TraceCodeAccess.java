package com.nexus.erp.wm.trace;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.trace.CodeObjectType;
import java.util.EnumSet;
import java.util.Set;

/** 保持各类对象既有查询权限边界，不因统一入口授予额外对象访问权。 */
public final class TraceCodeAccess {
    private TraceCodeAccess() { }
    public static Set<CodeObjectType> allowed() {
        StpUtil.checkLogin();
        EnumSet<CodeObjectType> types=EnumSet.noneOf(CodeObjectType.class);
        boolean stock=any("wm:stock:query","wm:stock:list","wm:trace:query");
        if(erpSerialAllowed() || any("mes:pro:sn:query","mes:pro:sn:list","mes:pro:trace:query")) types.add(CodeObjectType.SN);
        if(stock || any("wm:batch:query","wm:batch:list")) types.add(CodeObjectType.BATCH);
        if(stock || any("bd:material:query","bd:material:list")) types.add(CodeObjectType.MATERIAL);
        if(ErpDocumentCodeResolver.hasDocumentPermission())types.add(CodeObjectType.DOCUMENT);
        return types;
    }
    public static boolean mesSerialOnly(){return !erpSerialAllowed() && any("mes:pro:sn:query","mes:pro:sn:list","mes:pro:trace:query");}
    private static boolean erpSerialAllowed(){return any("wm:stock:query","wm:stock:list","wm:trace:query","wm:sn:query","wm:sn:list");}
    private static boolean any(String... permissions) {for(String permission:permissions) if(StpUtil.hasPermission(permission)) return true;return false;}
}
