package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchWorker;
import org.dromara.erp.sch.domain.bo.SchWorkerBo;
import org.dromara.erp.sch.domain.vo.SchWorkerVo;
import org.dromara.erp.sch.mapper.SchWorkerMapper;
import org.dromara.erp.sch.service.ISchWorkerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 生产工人档案Service业务层处理
 *
 * @author nexus
 * @date 2025-12-19
 */
@RequiredArgsConstructor
@Service
public class SchWorkerServiceImpl implements ISchWorkerService {

    private final SchWorkerMapper baseMapper;

    @Override
    public TableDataInfo<SchWorkerVo> selectPageList(SchWorkerBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchWorker> lqw = buildQueryWrapper(bo);
        Page<SchWorkerVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchWorkerVo> selectList(SchWorkerBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchWorkerVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchWorkerBo bo) {
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchWorkerBo bo) {
        SchWorker entity = MapstructUtils.convert(bo, SchWorker.class);
        return baseMapper.updateById(entity);
    }

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
            lqw.eq(StringUtils.isNotBlank(bo.getWorkStatus()), SchWorker::getWorkStatus, bo.getWorkStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getWorkCategory()), SchWorker::getWorkCategory, bo.getWorkCategory());
        }
        return lqw;
    }
}
