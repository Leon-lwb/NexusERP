package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchWorkerMapper;
import com.nexus.core.sch.domain.SchWorker;
import com.nexus.core.sch.service.ISchWorkerService;

/**
 * 生产工人档案Service业务层处理
 *
 * @author nexus
 * @date 2025-12-19
 */
@Service
public class SchWorkerServiceImpl extends ServiceImpl<SchWorkerMapper, SchWorker> implements ISchWorkerService {

    @Autowired
    private SchWorkerMapper schWorkerMapper;

    /**
     * 查询生产工人档案
     *
     * @param workerId 生产工人档案主键
     * @return 生产工人档案
     */
    @Override
    public SchWorker selectSchWorkerByWorkerId(Long workerId) {
        return schWorkerMapper.selectSchWorkerByWorkerId(workerId);
    }

    /**
     * 查询生产工人档案列表
     *
     * @param schWorker 生产工人档案
     * @return 生产工人档案
     */
    @Override
    public List<SchWorker> selectSchWorkerList(SchWorker schWorker) {
        return schWorkerMapper.selectSchWorkerList(schWorker);
    }

    /**
     * 新增生产工人档案
     *
     * @param schWorker 生产工人档案
     * @return 结果
     */
    @Override
    public int insertSchWorker(SchWorker schWorker) {
                schWorker.setCreateTime(DateUtils.getNowDate());
            return schWorkerMapper.insertSchWorker(schWorker);
    }

    /**
     * 修改生产工人档案
     *
     * @param schWorker 生产工人档案
     * @return 结果
     */
    @Override
    public int updateSchWorker(SchWorker schWorker) {
                schWorker.setUpdateTime(DateUtils.getNowDate());
        return schWorkerMapper.updateSchWorker(schWorker);
    }

    /**
     * 批量删除生产工人档案
     *
     * @param workerIds 需要删除的生产工人档案主键
     * @return 结果
     */
    @Override
    public int deleteSchWorkerByWorkerIds(Long[] workerIds) {
        return schWorkerMapper.deleteSchWorkerByWorkerIds(workerIds);
    }

    /**
     * 删除生产工人档案信息
     *
     * @param workerId 生产工人档案主键
     * @return 结果
     */
    @Override
    public int deleteSchWorkerByWorkerId(Long workerId) {
        return schWorkerMapper.deleteSchWorkerByWorkerId(workerId);
    }
}
