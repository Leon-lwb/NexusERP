package com.nexus.core.sch.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.sch.domain.SchWorker;
import com.nexus.core.sch.domain.dto.SchWorkerDTO;

/**
 * 生产工人档案Mapper接口
 *
 * @author nexus
 * @date 2025-12-19
 */
public interface SchWorkerMapper extends BaseMapper<SchWorker> {
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
     * 删除生产工人档案
     *
     * @param workerId 生产工人档案主键
     * @return 结果
     */
    public int deleteSchWorkerByWorkerId(Long workerId);

    /**
     * 批量删除生产工人档案
     *
     * @param workerIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchWorkerByWorkerIds(Long[] workerIds);
    
    /**
     * 新增生产工人档案(DTO版本)
     *
     * @param schWorkerDTO 生产工人档案DTO
     * @return 结果
     */
    public int insertSchWorkerDTO(SchWorkerDTO schWorkerDTO);
}