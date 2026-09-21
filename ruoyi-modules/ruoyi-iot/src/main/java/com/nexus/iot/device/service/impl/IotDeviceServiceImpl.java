package com.nexus.iot.device.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import io.github.linpeilie.Converter;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.domain.IotProduct;
import com.nexus.iot.device.domain.bo.IotDeviceBo;
import com.nexus.iot.device.domain.vo.IotDeviceSecretVo;
import com.nexus.iot.device.domain.vo.IotDeviceVo;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import com.nexus.iot.device.mapper.IotProductMapper;
import com.nexus.iot.device.domain.IotDeviceStatus;
import com.nexus.iot.device.domain.vo.IotDeviceOpsVo;
import com.nexus.iot.device.group.mapper.IotDeviceGroupRelMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagRelMapper;
import com.nexus.iot.device.group.service.IotDeviceFilterResolver;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.pipeline.IotDeviceReportTimeCache;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceServiceImpl implements IIotDeviceService {

    private final IotDeviceMapper baseMapper;
    private final IotProductMapper productMapper;
    private final IotDeviceGroupRelMapper groupRelMapper;
    private final IotDeviceTagRelMapper tagRelMapper;
    private final IotDeviceFilterResolver deviceFilterResolver;
    private final IotDeviceReportTimeCache reportTimeCache;
    /**
     * mapstruct-plus 转换器（注入而非静态 {@code MapstructUtils}：后者静态初始化依赖 Spring 容器，单测无法打桩）。
     */
    private final Converter converter;

    /**
     * 分页查询设备列表，按条件构建查询并转换为 VO 分页结果。
     *
     * @param bo        查询条件（编码/名称/产品/协议/状态等）
     * @param pageQuery 分页参数
     * @return 设备 VO 分页结果
     */
    @Override
    public TableDataInfo<IotDeviceVo> selectPageList(IotDeviceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<IotDevice> lqw = buildQueryWrapper(bo);
        Page<IotDeviceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询设备列表（不分页），用于导出等场景。
     *
     * @param bo 查询条件
     * @return 设备 VO 列表
     */
    @Override
    public List<IotDeviceVo> selectList(IotDeviceBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据主键查询设备详情（VO）。
     *
     * @param id 设备 ID
     * @return 设备 VO，不存在返回 null
     */
    @Override
    public IotDeviceVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增设备，自动生成 device_secret，并在未指定协议/产品名时从所属产品回填。
     * 新建设备默认连接状态为 OFFLINE。
     *
     * @param bo 设备业务对象
     * @return 插入影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotDeviceBo bo) {
        IotDevice entity = converter.convert(bo, IotDevice.class);
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
        // 连接状态由 MQTT Broker 独占维护，客户端传值一律忽略
        entity.setConnectionStatus("OFFLINE");
        // 生命周期状态：新增固定 ACTIVE，禁用/注销必须走专用端点（CAS），避免前端直改状态绕过校验
        entity.setStatus(IotDeviceStatus.ACTIVE);
        return baseMapper.insert(entity);
    }

    /**
     * 修改设备，切换产品时回填产品名称与协议；密钥不在此更新以防前端覆盖。
     *
     * @param bo 设备业务对象
     * @return 更新影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotDeviceBo bo) {
        IotDevice entity = converter.convert(bo, IotDevice.class);
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
        // 服务端权威字段白名单（规范 §2.1：BO 与服务端两侧同时堵）：
        // 密钥 → 专用重置接口；连接状态 → MQTT Broker 独占维护；生命周期状态 → 专用状态变更端点(CAS)。
        entity.setDeviceSecret(null);
        entity.setConnectionStatus(null);
        entity.setStatus(null);
        return baseMapper.updateById(entity);
    }

    /**
     * 重置设备密钥（轮换 device_secret）。
     * <p>
     * 旧密钥立即失效：MQTT 认证按库内 device_secret 常量时间比对，写入新值后旧密码即无法通过认证。
     * 仅返回新密钥，不返回旧值；不影响设备其他字段。
     *
     * @param deviceId 设备ID
     * @return 含新密钥的结果对象；设备不存在返回 null
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceSecretVo resetSecret(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        IotDevice device = baseMapper.selectById(deviceId);
        if (device == null) {
            log.warn("重置设备密钥失败：设备不存在，deviceId={}", deviceId);
            return null;
        }
        String newSecret = UUID.randomUUID().toString().replace("-", "");
        // 条件更新：仅当设备仍存在时写入，影响 0 行视为并发删除，不返回密钥
        int rows = baseMapper.update(null, Wrappers.<IotDevice>lambdaUpdate()
            .set(IotDevice::getDeviceSecret, newSecret)
            .eq(IotDevice::getDeviceId, deviceId));
        if (rows == 0) {
            log.warn("重置设备密钥失败：设备已被删除，deviceId={}", deviceId);
            return null;
        }
        log.info("设备密钥已重置：deviceId={}, deviceCode={}", deviceId, device.getDeviceCode());
        IotDeviceSecretVo vo = new IotDeviceSecretVo();
        vo.setDeviceId(device.getDeviceId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceSecret(newSecret);
        return vo;
    }

    /**
     * 批量逻辑删除设备。
     *
     * @param ids 设备 ID 数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据设备编码查询设备实体（含 device_secret），供 MQTT 认证使用。
     *
     * @param deviceCode 设备编码（即 MQTT ClientID）
     * @return 设备实体，编码为空或查不到时返回 null
     */
    @Override
    public IotDevice selectEntityByCode(String deviceCode) {
        if (StringUtils.isBlank(deviceCode)) {
            return null;
        }
        LambdaQueryWrapper<IotDevice> lqw = Wrappers.<IotDevice>lambdaQuery()
            .eq(IotDevice::getDeviceCode, deviceCode);
        return baseMapper.selectOne(lqw);
    }

    /**
     * 更新设备连接状态，上线时同步刷新最后在线时间；由 MQTT Broker 连接/断开回调调用。
     *
     * @param deviceId 设备 ID
     * @param online   true=ONLINE，false=OFFLINE
     */
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

    /**
     * 查询状态为 ONLINE 但最后在线时间早于 deadline 的设备，用于心跳任务识别异常掉线。
     *
     * @param deadline 截止时间
     * @return 超时在线设备实体集合（deadline 为空时返回空）
     */
    @Override
    public List<IotDevice> selectStaleOnlineDevices(Date deadline) {
        if (deadline == null) {
            return List.of();
        }
        LambdaQueryWrapper<IotDevice> lqw = Wrappers.<IotDevice>lambdaQuery()
            .eq(IotDevice::getConnectionStatus, "ONLINE")
            .lt(IotDevice::getLastOnlineTime, deadline);
        return baseMapper.selectList(lqw);
    }

    /**
     * 仅更新设备关联的 MES 设备 ID（可置空解除关联），不影响其他字段。
     *
     * @param deviceId        IoT 设备 ID
     * @param mesEquipmentId  MES 设备 ID（可空）
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateMesEquipmentLink(Long deviceId, Long mesEquipmentId) {
        if (deviceId == null) {
            return false;
        }
        LambdaUpdateWrapper<IotDevice> wrapper = Wrappers.<IotDevice>lambdaUpdate()
            .set(IotDevice::getMesEquipmentId, mesEquipmentId)
            .eq(IotDevice::getDeviceId, deviceId);
        int rows = baseMapper.update(null, wrapper);
        return rows > 0;
    }

    /**
     * 变更设备生命周期状态（激活/禁用/注销），条件更新（CAS）。
     *
     * @param deviceId     设备ID
     * @param targetStatus 目标状态
     * @param remark       变更原因
     * @return 更新影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long deviceId, String targetStatus, String remark) {
        if (deviceId == null) {
            throw new ServiceException("设备ID不能为空");
        }
        if (!IotDeviceStatus.isKnown(targetStatus)) {
            throw new ServiceException("非法的目标状态：" + targetStatus);
        }
        IotDevice device = baseMapper.selectById(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在：" + deviceId);
        }
        if (!IotDeviceStatus.canTransition(device.getStatus(), targetStatus)) {
            throw new ServiceException("设备状态非法流转：" + device.getStatus() + " -> " + targetStatus
                + "（注销为终态不可复活；目标状态需与当前不同）");
        }
        Long userId = LoginHelper.getUserId();
        Date now = new Date();
        LambdaUpdateWrapper<IotDevice> wrapper = Wrappers.<IotDevice>lambdaUpdate()
            .set(IotDevice::getStatus, targetStatus)
            .set(StringUtils.isNotBlank(remark), IotDevice::getRemark, remark)
            .set(IotDevice::getUpdateBy, userId)
            .set(IotDevice::getUpdateTime, now);
        // CAS：仅当状态仍为读取时的值才更新（并发下另一次变更会使其影响 0 行）
        if (device.getStatus() == null) {
            wrapper.isNull(IotDevice::getStatus);
        } else {
            wrapper.eq(IotDevice::getStatus, device.getStatus());
        }
        if (!IotDeviceStatus.canConnect(targetStatus)) {
            // 禁用/注销：立即置离线，避免列表仍显示 ONLINE（真实连接由 Broker 断开或心跳任务收敛）
            wrapper.set(IotDevice::getConnectionStatus, "OFFLINE");
        }
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("设备状态变更失败：状态已被其他操作变更，请刷新后重试");
        }
        log.warn("设备生命周期状态变更：deviceId={}, deviceCode={}, {} -> {}, operator={}, remark={}",
            deviceId, device.getDeviceCode(), device.getStatus(), targetStatus, userId, remark);
        return rows;
    }

    /**
     * 查询设备运维详情。
     *
     * @param deviceId 设备ID
     * @return 运维详情VO，设备不存在返回 null
     */
    @Override
    public IotDeviceOpsVo selectOps(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        IotDevice device = baseMapper.selectById(deviceId);
        if (device == null) {
            return null;
        }
        IotDeviceOpsVo vo = new IotDeviceOpsVo();
        vo.setDeviceId(device.getDeviceId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setProductId(device.getProductId());
        vo.setProductName(device.getProductName());
        vo.setProtocolType(device.getProtocolType());
        vo.setStatus(device.getStatus());
        vo.setConnectionStatus(device.getConnectionStatus());
        vo.setLastOnlineTime(device.getLastOnlineTime());
        vo.setFirmwareVersion(device.getFirmwareVersion());
        vo.setIpAddress(device.getIpAddress());
        vo.setLocation(device.getLocation());
        vo.setMesEquipmentId(device.getMesEquipmentId());
        vo.setLastReportTime(reportTimeCache.get(deviceId));
        String tenantId = TenantHelper.getTenantId();
        vo.setGroupIds(groupRelMapper.selectGroupIdsByDevice(tenantId, deviceId));
        vo.setTagIds(tagRelMapper.selectTagIdsByDevice(tenantId, deviceId));
        return vo;
    }

    private LambdaQueryWrapper<IotDevice> buildQueryWrapper(IotDeviceBo bo) {
        LambdaQueryWrapper<IotDevice> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getDeviceCode()), IotDevice::getDeviceCode, bo.getDeviceCode());
        lqw.like(StringUtils.isNotBlank(bo.getDeviceName()), IotDevice::getDeviceName, bo.getDeviceName());
        lqw.eq(bo.getProductId() != null, IotDevice::getProductId, bo.getProductId());
        lqw.eq(StringUtils.isNotBlank(bo.getProtocolType()), IotDevice::getProtocolType, bo.getProtocolType());
        lqw.eq(StringUtils.isNotBlank(bo.getConnectionStatus()), IotDevice::getConnectionStatus, bo.getConnectionStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotDevice::getStatus, bo.getStatus());
        // 分组 / 标签筛选（§4#2）：解析为设备ID集合后下推 IN，避免内存全表过滤
        List<Long> filteredIds = deviceFilterResolver.resolveDeviceIds(
            bo.getGroupId(), bo.getIncludeSubGroup(), bo.getTagIds());
        if (filteredIds != null) {
            if (filteredIds.isEmpty()) {
                // 筛选条件命中空集 → 必须"查不到"，而不是忽略条件返回全量
                lqw.apply("1 = 0");
            } else {
                lqw.in(IotDevice::getDeviceId, filteredIds);
            }
        }
        return lqw;
    }
}
