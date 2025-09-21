package com.nexus.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;

import com.nexus.bd.domain.BdClient;
import com.nexus.bd.mapper.BdClientMapper;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.bd.service.IBdClientService;

/**
 * 客户信息Service业务层处理
 *
 * @author nexus
 * @date 2025-09-18
 */
@Service
public class BdClientServiceImpl extends ServiceImpl<BdClientMapper, BdClient> implements IBdClientService {

    @Autowired
    private BdClientMapper bdClientMapper;

    /**
     * 查询客户信息
     *
     * @param clientId 客户信息主键
     * @return 客户信息
     */
    @Override
    public BdClient selectBdClientByClientId(Long clientId) {
        return bdClientMapper.selectBdClientByClientId(clientId);
    }

    /**
     * 查询客户信息列表
     *
     * @param bdClient 客户信息
     * @return 客户信息
     */
    @Override
    public List<BdClient> selectBdClientList(BdClient bdClient) {
        return bdClientMapper.selectBdClientList(bdClient);
    }

    /**
     * 新增客户信息
     *
     * @param bdClient 客户信息
     * @return 结果
     */
    @Override
    public int insertBdClient(BdClient bdClient) {
                bdClient.setCreateTime(DateUtils.getNowDate());
            return bdClientMapper.insertBdClient(bdClient);
    }

    /**
     * 修改客户信息
     *
     * @param bdClient 客户信息
     * @return 结果
     */
    @Override
    public int updateBdClient(BdClient bdClient) {
                bdClient.setUpdateTime(DateUtils.getNowDate());
        return bdClientMapper.updateBdClient(bdClient);
    }

    /**
     * 批量删除客户信息
     *
     * @param clientIds 需要删除的客户信息主键
     * @return 结果
     */
    @Override
    public int deleteBdClientByClientIds(Long[] clientIds) {
        return bdClientMapper.deleteBdClientByClientIds(clientIds);
    }

    /**
     * 删除客户信息信息
     *
     * @param clientId 客户信息主键
     * @return 结果
     */
    @Override
    public int deleteBdClientByClientId(Long clientId) {
        return bdClientMapper.deleteBdClientByClientId(clientId);
    }
}
