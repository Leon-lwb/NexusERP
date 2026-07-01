package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.domain.bo.SchWorkerBo;
import com.nexus.erp.sch.domain.vo.SchWorkerVo;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchWorkerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 生产工人档案Service业务层处理
 *
 * @author nexus
 * @date 2025-12-19
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchWorkerServiceImpl implements ISchWorkerService {

    private final SchWorkerMapper baseMapper;

    /**
     * 分页查询生产工人档案列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产工人档案分页列表
     */
    @Override
    public TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchWorker> lqw = buildQueryWrapper(bo);
        Page<SchWorkerVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询生产工人档案列表
     *
     * @param bo 查询参数
     * @return 生产工人档案列表
     */
    @Override
    public List<SchWorkerVo> selectList(SchWorkerBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取生产工人档案详情
     *
     * @param id 工人档案ID
     * @return 生产工人档案详情
     */
    @Override
    public SchWorkerVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchWorkerBo bo) {
        checkUserIdUnique(bo.getUserId(), null);
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产工人档案
     *
     * @param bo 生产工人档案业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchWorkerBo bo) {
        checkUserIdUnique(bo.getUserId(), bo.getWorkerId());
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除生产工人档案
     *
     * @param ids 工人档案ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchWorker> buildQueryWrapper(SchWorkerBo bo) {
        LambdaQueryWrapper<SchWorker> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getUserName()), SchWorker::getUserName, bo.getUserName());
            lqw.eq(StringUtils.isNotBlank(bo.getEmployeeId()), SchWorker::getEmployeeId, bo.getEmployeeId());
            lqw.eq(bo.getUserId() != null, SchWorker::getUserId, bo.getUserId());
            lqw.eq(bo.getWorkshopId() != null, SchWorker::getWorkshopId, bo.getWorkshopId());
            lqw.eq(StringUtils.isNotBlank(bo.getWorkStatus()), SchWorker::getWorkStatus, bo.getWorkStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getWorkCategory()), SchWorker::getWorkCategory, bo.getWorkCategory());
        }
        return lqw;
    }

    /**
     * 校验一个系统用户只能建档一个工人档案（无账号工人 userId 为空，跳过校验）
     *
     * @param userId           关联的系统用户ID
     * @param excludeWorkerId  排除的工人档案ID（修改时排除自身）
     */
    private void checkUserIdUnique(Long userId, Long excludeWorkerId) {
        if (userId == null) {
            return;
        }
        LambdaQueryWrapper<SchWorker> lqw = Wrappers.lambdaQuery();
        lqw.eq(SchWorker::getUserId, userId);
        lqw.ne(excludeWorkerId != null, SchWorker::getWorkerId, excludeWorkerId);
        Long count = baseMapper.selectCount(lqw);
        if (count != null && count > 0) {
            throw new ServiceException("该系统用户已建档工人档案，不能重复建档");
        }
    }
}
