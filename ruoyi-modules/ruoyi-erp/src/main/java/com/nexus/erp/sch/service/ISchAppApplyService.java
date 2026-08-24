package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchApplyApproveBo;
import com.nexus.erp.sch.domain.bo.SchLeaveApplyBo;
import com.nexus.erp.sch.domain.bo.SchOutdoorPunchBo;
import com.nexus.erp.sch.domain.bo.SchSupplementApplyBo;
import com.nexus.erp.sch.domain.vo.SchLeaveApplyVo;
import com.nexus.erp.sch.domain.vo.SchOutdoorPunchVo;
import com.nexus.erp.sch.domain.vo.SchSupplementApplyVo;

/**
 * App 考勤申请Service接口（补卡/外勤/请假）
 *
 * @author nexus
 * @date 2026-08-24
 */
public interface ISchAppApplyService {

    /**
     * 提交补卡申请（校验打卡规则允许补卡 + 截止天数 + 同日重复申请）
     *
     * @param workerId   申请人(工人)ID
     * @param workerName 申请人姓名
     * @param bo         补卡申请参数
     * @return 申请单ID
     */
    Long submitSupplementApply(Long workerId, String workerName, SchSupplementApplyBo bo);

    /**
     * 分页查询本人补卡申请
     */
    TableDataInfo<SchSupplementApplyVo> selectMySupplementApplies(Long workerId, String startDate, String endDate,
                                                                  String applyStatus, PageQuery pageQuery);

    /**
     * 分页查询待审批补卡申请（全部工人）
     */
    TableDataInfo<SchSupplementApplyVo> selectPendingSupplementApplies(PageQuery pageQuery);

    /**
     * 审批补卡申请（通过时按当日排班回写考勤记录）
     */
    void approveSupplementApply(SchApplyApproveBo bo);

    /**
     * 提交外勤打卡（校验打卡规则允许外勤 + 拍照要求，照片存 MinIO）
     *
     * @return 外勤打卡ID
     */
    Long submitOutdoorPunch(Long workerId, String workerName, SchOutdoorPunchBo bo);

    /**
     * 分页查询本人外勤打卡记录
     */
    TableDataInfo<SchOutdoorPunchVo> selectMyOutdoorPunches(Long workerId, String startDate, String endDate,
                                                            PageQuery pageQuery);

    /**
     * 提交请假申请（校验起止日期，自动计算时长天数）
     *
     * @return 请假申请ID
     */
    Long submitLeaveApply(Long workerId, String workerName, SchLeaveApplyBo bo);

    /**
     * 分页查询本人请假申请
     */
    TableDataInfo<SchLeaveApplyVo> selectMyLeaveApplies(Long workerId, String startDate, String endDate,
                                                        String applyStatus, PageQuery pageQuery);

    /**
     * 分页查询待审批请假申请（全部工人）
     */
    TableDataInfo<SchLeaveApplyVo> selectPendingLeaveApplies(PageQuery pageQuery);

    /**
     * 审批请假申请（通过时将请假区间内考勤记录置为 leave 状态，月度统计生效）
     */
    void approveLeaveApply(SchApplyApproveBo bo);
}
