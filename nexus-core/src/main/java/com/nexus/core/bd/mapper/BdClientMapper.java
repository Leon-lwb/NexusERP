package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdClient;

/**
 * 客户信息Mapper接口
 *
 * @author nexus
 * @date 2025-09-18
 */
public interface BdClientMapper extends BaseMapper<BdClient> {
    /**
     * 查询客户信息
     *
     * @param clientId 客户信息主键
     * @return 客户信息
     */
    public BdClient selectBdClientByClientId(Long clientId);

    /**
     * 查询客户信息列表
     *
     * @param bdClient 客户信息
     * @return 客户信息集合
     */
    public List<BdClient> selectBdClientList(BdClient bdClient);

    /**
     * 新增客户信息
     *
     * @param bdClient 客户信息
     * @return 结果
     */
    public int insertBdClient(BdClient bdClient);

    /**
     * 修改客户信息
     *
     * @param bdClient 客户信息
     * @return 结果
     */
    public int updateBdClient(BdClient bdClient);

    /**
     * 删除客户信息
     *
     * @param clientId 客户信息主键
     * @return 结果
     */
    public int deleteBdClientByClientId(Long clientId);

    /**
     * 批量删除客户信息
     *
     * @param clientIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdClientByClientIds(Long[] clientIds);
}
