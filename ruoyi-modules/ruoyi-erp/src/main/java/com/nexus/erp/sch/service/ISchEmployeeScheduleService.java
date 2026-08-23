package com.nexus.erp.sch.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.bo.SchEmployeeScheduleBo;
import com.nexus.erp.sch.domain.vo.SchEmployeeScheduleVo;

import java.util.List;

/**
 * 员工排班明细Service接口
 *
 * @author nexus
 * @date 2025-12-30
 */
public interface ISchEmployeeScheduleService {

    /**
     * 分页查询员工排班明细列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 员工排班明细分页列表
     */
    TableDataInfo<SchEmployeeScheduleVo> selectPageList(SchEmployeeScheduleBo bo, PageQuery pageQuery);

    /**
     * 查询员工排班明细列表
     *
     * @param bo 查询参数
     * @return 员工排班明细列表
     */
    List<SchEmployeeScheduleVo> selectList(SchEmployeeScheduleBo bo);

    /**
     * 根据ID获取员工排班明细详情
     *
     * @param id 排班明细ID
     * @return 员工排班明细详情
     */
    SchEmployeeScheduleVo selectById(Long id);

    /**
     * 新增员工排班明细
     *
     * @param bo 员工排班明细业务对象
     * @return 影响行数
     */
    int insertByBo(SchEmployeeScheduleBo bo);

    /**
     * 修改员工排班明细
     *
     * @param bo 员工排班明细业务对象
     * @return 影响行数
     */
    int updateByBo(SchEmployeeScheduleBo bo);

    /**
     * 批量删除员工排班明细
     *
     * @param ids 排班明细ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 生成员工排班明细
     * <p>按班组排班模板(sch_team_schedule)精确展开班组→班次绑定与排班日期;
     * 无模板的计划回退轮转算法; 仅处理已发布且起止日期齐全的计划</p>
     *
     * @param planId 排班计划ID(空则处理全部已发布计划; 按计划范围清理旧明细)
     * @return 生成记录数
     */
    int generate(Long planId);
}
