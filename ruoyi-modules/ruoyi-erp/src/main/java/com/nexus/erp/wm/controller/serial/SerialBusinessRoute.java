package com.nexus.erp.wm.controller.serial;

import java.util.*;

/** 原业务 URL 与真实来源表的固定目录，禁止客户端指定表名或租户。 */
public record SerialBusinessRoute(String type, String path, String headTable, String headKey,
                                  String headProperty, String lineTable, String linesProperty,
                                  String entity, String lineEntity) {
    private static SerialBusinessRoute route(String type,String path,String table,String key,String property,
                                              String line,String lines,String entity,String lineEntity) {
        return new SerialBusinessRoute(type,path,table,key,property,line,lines,entity,lineEntity);
    }
    public static final List<SerialBusinessRoute> ALL=List.of(
        route("WM_MATERIAL_RECEIVE","receive","wm_material_receive","receive_id","receiveId","wm_material_receive_line","wmMaterialReceiveLineList","WmMaterialReceive","WmMaterialReceiveLine"),
        route("WM_PRODUCTION_ISSUE","productionissue","wm_production_issue","issue_id","issueId","wm_production_issue_line","wmProductionIssueLineList","WmProductionIssue","WmProductionIssueLine"),
        route("WM_MATERIAL_RETURN","return","wm_material_return","return_id","returnId","wm_material_return_line","lines","WmMaterialReturn","WmMaterialReturnLine"),
        route("WM_TRANSFER","transfer","wm_transfer","transfer_id","transferId","wm_transfer_line","wmTransferLineList","WmTransfer","WmTransferLine"),
        route("WM_STOCK_TAKING","stocktaking","wm_stock_taking_plan","plan_id","planId","wm_stock_taking_line","wmStockTakingLineList","WmStockTakingPlan","WmStockTakingLine"),
        route("WM_SALES_ISSUE","salesissue","wm_sales_issue","issue_id","issueId","wm_sales_issue_line","wmSalesIssueLineList","WmSalesIssue","WmSalesIssueLine"),
        route("WM_MISC_ISSUE","miscissue","wm_misc_issue","issue_id","issueId","wm_misc_issue_line","wmMiscIssueLineList","WmMiscIssue","WmMiscIssueLine"),
        route("WM_MISC_RECPT","miscrecpt","wm_misc_recpt","recpt_id","recptId","wm_misc_recpt_line","wmMiscRecptLineList","WmMiscRecpt","WmMiscRecptLine"),
        route("SO_RETURN","/erp/so/return","so_return","return_id","returnId","so_return_line","soReturnLineList","SoReturn","SoReturnLine"),
        route("PU_PURCHASE_RETURN","/erp/pu/return","pu_purchase_return","return_id","returnId","pu_purchase_return_line","puPurchaseReturnLineList","PuPurchaseReturn","PuPurchaseReturnLine"),
        route("PRD_REWORK_ORDER","/erp/prd/rework","prd_rework_order","rework_id","reworkId",null,null,"PrdReworkOrder",null),
        route("WM_SUBCONTRACT_ISSUE","sc-issue","wm_subcontract_issue","issue_id","issueId","wm_subcontract_issue_line","lines","WmSubcontractIssue","WmSubcontractIssueLine"),
        route("WM_SUBCONTRACT_RECEIVE","sc-receive","wm_subcontract_receive","receive_id","receiveId",null,null,"WmSubcontractReceive",null),
        route("PRD_WORK_ORDER","productionrecpt","wm_production_recpt","recpt_id","recptId",null,null,"WmProductionRecpt",null)
    );
    public static SerialBusinessRoute ofPath(String uri) {
        String p=uri.toLowerCase(Locale.ROOT);
        for(var r:ALL) {
            List<String> roots=r.path.startsWith("/")?List.of(r.path):List.of("/erp/wm/"+r.path,"/app/pda/wm/"+(r.type.equals("WM_PRODUCTION_ISSUE")?"issue":r.path));
            for(String root:roots)if(p.equals(root)||p.startsWith(root+"/"))return r;
        }
        return null;
    }
    /** 收货详情页还会读取既有独立行/分配列表，这些读URL沿用同一来源版本。 */
    public static SerialBusinessRoute forReadPath(String uri) {
        for(String root:List.of("/erp/wm/detail","/erp/wm/receiveline"))
            if(uri.equals(root)||uri.startsWith(root+"/"))return ofType("WM_MATERIAL_RECEIVE");
        return ofPath(uri);
    }
    public static SerialBusinessRoute ofType(String type) {
        String effective="PRD_REWORK_ORDER_RECEIVE".equals(type)?"PRD_REWORK_ORDER":type;
        return ALL.stream().filter(r->r.type.equals(effective)).findFirst().orElseThrow(()->new IllegalArgumentException("不支持的业务来源"));
    }
    /** 按真实controller动作区分过账和状态流转，避免漏检或把完工状态误作库存效果。 */
    public boolean postsOn(String action) {
        return switch(type) {
            case "WM_MATERIAL_RECEIVE" -> Set.of("executeReceive","finish").contains(action);
            case "WM_PRODUCTION_ISSUE" -> Set.of("execute","pick").contains(action);
            case "WM_MATERIAL_RETURN" -> action.equals("finish");
            case "WM_TRANSFER" -> Set.of("execute","shelf").contains(action);
            case "WM_STOCK_TAKING" -> Set.of("postAdjustment","post").contains(action);
            case "WM_SALES_ISSUE" -> action.equals("complete");
            case "WM_MISC_ISSUE","WM_MISC_RECPT","SO_RETURN","PU_PURCHASE_RETURN","PRD_WORK_ORDER" -> action.equals("execute");
            case "PRD_REWORK_ORDER" -> Set.of("issue","receive").contains(action);
            case "WM_SUBCONTRACT_ISSUE","WM_SUBCONTRACT_RECEIVE" -> action.equals("post");
            default -> false;
        };
    }
    public boolean whole() {return lineTable==null;}
    public String sourceType(String uri) {return type.equals("PRD_REWORK_ORDER")&&uri.contains("/receive/")?"PRD_REWORK_ORDER_RECEIVE":type;}
    public Long pathId(String uri) {
        for(String part:uri.split("/"))if(part.matches("[1-9][0-9]*"))return Long.valueOf(part);
        return null;
    }
}
