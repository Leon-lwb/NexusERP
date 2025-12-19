package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchWorker;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 生产工人档案Service接口
 *
 * @author nexus
 * @date 2025-12-19
 */
public interface ISchWorkerService extends IService<SchWorker> {
    /**
     * 查询生产工人档案
     *
     * @param workerId 生产工人档案主键
     * @return 生产工人档案
     */
    public SchWorker selectSchWorkerByWorkerId(Long workerId);

    /**
     * 查询生产工人档案列表
     *
     * @param schWorker 生产工人档案
     * @return 生产工人档案集合
     */
    public List<SchWorker> selectSchWorkerList(SchWorker schWorker);

    /**
     * 新增生产工人档案
     *
     * @param schWorker 生产工人档案
     * @return 结果
     */
    public int insertSchWorker(SchWorker schWorker);

    /**
     * 修改生产工人档案
     *
     * @param schWorker 生产工人档案
     * @return 结果
     */
    public int updateSchWorker(SchWorker schWorker);

    /**
     * 批量删除生产工人档案
     *
     * @param workerIds 需要删除的生产工人档案主键集合
     * @return 结果
     */
    public int deleteSchWorkerByWorkerIds(Long[] workerIds);

    /**
     * 删除生产工人档案信息
     *
     * @param workerId 生产工人档案主键
     * @return 结果
     */
    public int deleteSchWorkerByWorkerId(Long workerId);
}
