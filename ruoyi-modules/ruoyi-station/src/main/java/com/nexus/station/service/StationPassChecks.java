package com.nexus.station.service;

import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProSn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询展示与锁定写入口共同使用的四重业务规则；本类不读库、不产生写入。 */
final class StationPassChecks {
    private StationPassChecks() { }

    /** 根据调用方已读取的来源生成原有接口结构。 */
    static Map<String,Object> evaluate(String snCode, ProSn sn, ProFlowCardStep step, String process,
                                     List<ProFlowCardStep> unfinishedPrevious, long passCount,
                                     TestRecord latestTest, long openNcrCount) {
        List<Map<String,Object>> checks=new ArrayList<>(4);
        Map<String,Object> info=new LinkedHashMap<>();
        info.put("currentProcessName",step==null?null:step.getProcessName());
        if(sn==null) {
            checks.add(check(StationPassService.CHECK_STATION_MATCH,false,"SN不存在: "+snCode));
        } else {
            info.put("woNo",sn.getWoNo()); info.put("cardNo",sn.getCardNo()); info.put("materialName",sn.getMaterialName());
            String prev=step==null?"SN 未找到当前工序记录":unfinishedPrevious.isEmpty()?null:
                "前道工序未完成: "+String.join(", ",unfinishedPrevious.stream().map(s->s.getProcessName()+"("+s.getStepStatus()+")").toList());
            checks.add(check(StationPassService.CHECK_PREV_STEP,prev==null,prev));
            String match=process==null?"无法解析或核验工位工序编码":step==null||!process.equals(step.getProcessCode())?
                "工位工序与SN当前工序不匹配":!"IN_PRODUCTION".equals(sn.getSnStatus())?"SN 非在制状态":null;
            checks.add(check(StationPassService.CHECK_STATION_MATCH,match==null,match));
            checks.add(check(StationPassService.CHECK_DUPLICATE,passCount==0,passCount==0?null:"该 SN 已在此工位报工过站(PASS), 禁止重复过站"));
            String hold=latestTest!=null&&"FAIL".equals(latestTest.getResult())?"最新综测结果 FAIL, 质量扣押":
                openNcrCount>0?"该 SN 所在流转卡存在未关闭 NCR 单, 质量扣押":null;
            checks.add(check(StationPassService.CHECK_QUALITY_HOLD,hold==null,hold));
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("pass",checks.stream().allMatch(c->Boolean.TRUE.equals(c.get("ok"))));
        result.put("snInfo",info); result.put("checks",checks); return result;
    }
    /** 单条检查保持客户端已使用的字段名与布尔值。 */
    private static Map<String,Object> check(String code,boolean ok,String message) {
        Map<String,Object> c=new LinkedHashMap<>(); c.put("code",code); c.put("ok",ok); c.put("message",message); return c;
    }
}
