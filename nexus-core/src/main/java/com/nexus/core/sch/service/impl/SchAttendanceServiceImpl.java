package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchAttendanceMapper;
import com.nexus.core.sch.domain.SchAttendance;
import com.nexus.core.sch.service.ISchAttendanceService;

/**
 * 考勤记录Service业务层处理
 *
 * @author nexus
 * @date 2026-01-06
 */
@Service
public class SchAttendanceServiceImpl extends ServiceImpl<SchAttendanceMapper, SchAttendance> implements ISchAttendanceService {

    @Autowired
    private SchAttendanceMapper schAttendanceMapper;

    /**
     * 查询考勤记录
     *
     * @param attendanceId 考勤记录主键
     * @return 考勤记录
     */
    @Override
    public SchAttendance selectSchAttendanceByAttendanceId(Long attendanceId) {
        return schAttendanceMapper.selectSchAttendanceByAttendanceId(attendanceId);
    }

    /**
     * 查询考勤记录列表
     *
     * @param schAttendance 考勤记录
     * @return 考勤记录
     */
    @Override
    public List<SchAttendance> selectSchAttendanceList(SchAttendance schAttendance) {
        return schAttendanceMapper.selectSchAttendanceList(schAttendance);
    }

    /**
     * 新增考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    @Override
    public int insertSchAttendance(SchAttendance schAttendance) {
                schAttendance.setCreateTime(DateUtils.getNowDate());
            return schAttendanceMapper.insertSchAttendance(schAttendance);
    }

    /**
     * 修改考勤记录
     *
     * @param schAttendance 考勤记录
     * @return 结果
     */
    @Override
    public int updateSchAttendance(SchAttendance schAttendance) {
                schAttendance.setUpdateTime(DateUtils.getNowDate());
        return schAttendanceMapper.updateSchAttendance(schAttendance);
    }

    /**
     * 批量删除考勤记录
     *
     * @param attendanceIds 需要删除的考勤记录主键
     * @return 结果
     */
    @Override
    public int deleteSchAttendanceByAttendanceIds(Long[] attendanceIds) {
        return schAttendanceMapper.deleteSchAttendanceByAttendanceIds(attendanceIds);
    }

    /**
     * 删除考勤记录信息
     *
     * @param attendanceId 考勤记录主键
     * @return 结果
     */
    @Override
    public int deleteSchAttendanceByAttendanceId(Long attendanceId) {
        return schAttendanceMapper.deleteSchAttendanceByAttendanceId(attendanceId);
    }
}
