package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.service.IIotDeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * IoT设备Service业务层处理
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceServiceImpl implements IIotDeviceService {

    private final IotDeviceMapper baseMapper;
    private final IotProductMapper productMapper;

    @Override
    public TableDataInfo<IotDeviceVo> selectPageList(IotDeviceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IotDevice> lqw = buildQueryWrapper(bo);
        Page<IotDeviceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<IotDeviceVo> selectList(IotDeviceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public IotDeviceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotDeviceBo bo) {
        IotDevice entity = MapstructUtils.convert(bo, IotDevice.class);
        // 自动生成设备密钥（UUID 去横线）
        entity.setDeviceSecret(UUID.randomUUID().toString().replace("-", ""));
        // 协议类型默认取产品的协议类型
        if (StringUtils.isBlank(entity.getProtocolType())) {
            IotProduct product = productMapper.selectById(bo.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在：" + bo.getProductId());
            }
            entity.setProtocolType(product.getProtocolType());
            entity.setProductName(product.getProductName());
        } else {
            // 即便指定了协议，仍回填产品名称冗余字段
            IotProduct product = productMapper.selectById(bo.getProductId());
            if (product != null) {
                entity.setProductName(product.getProductName());
            }
        }
        // 新设备默认离线
        if (StringUtils.isBlank(entity.getConnectionStatus())) {
            entity.setConnectionStatus("OFFLINE");
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotDeviceBo bo) {
        IotDevice entity = MapstructUtils.convert(bo, IotDevice.class);
        // 编辑时若切换了产品，回填产品名称与协议
        if (bo.getProductId() != null) {
            IotProduct product = productMapper.selectById(bo.getProductId());
            if (product != null) {
                entity.setProductName(product.getProductName());
                if (StringUtils.isBlank(entity.getProtocolType())) {
                    entity.setProtocolType(product.getProtocolType());
                }
            }
        }
        // 密钥不在此处更新（避免被前端覆盖），如需重置走专用接口
        entity.setDeviceSecret(null);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public IotDevice selectEntityByCode(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            return null;
        }
        LambdaQueryWrapper<IotDevice> lqw = Wrappers.<IotDevice>lambdaQuery()
            .eq(IotDevice::getDeviceCode, deviceCode);
        return baseMapper.selectOne(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConnectionStatus(Long deviceId, boolean online) {
        IotDevice update = new IotDevice();
        update.setDeviceId(deviceId);
        update.setConnectionStatus(online ? "ONLINE" : "OFFLINE");
        if (online) {
            update.setLastOnlineTime(new Date());
        }
        baseMapper.updateById(update);
    }

    private LambdaQueryWrapper<IotDevice> buildQueryWrapper(IotDeviceBo bo) {
        LambdaQueryWrapper<IotDevice> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getDeviceCode()), IotDevice::getDeviceCode, bo.getDeviceCode());
        lqw.like(StringUtils.isNotBlank(bo.getDeviceName()), IotDevice::getDeviceName, bo.getDeviceName());
        lqw.eq(bo.getProductId() != null, IotDevice::getProductId, bo.getProductId());
        lqw.eq(StringUtils.isNotBlank(bo.getProtocolType()), IotDevice::getProtocolType, bo.getProtocolType());
        lqw.eq(StringUtils.isNotBlank(bo.getConnectionStatus()), IotDevice::getConnectionStatus, bo.getConnectionStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotDevice::getStatus, bo.getStatus());
        return lqw;
    }
}
