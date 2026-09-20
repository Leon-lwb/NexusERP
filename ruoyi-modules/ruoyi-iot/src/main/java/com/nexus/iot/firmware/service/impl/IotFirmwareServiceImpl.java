package com.nexus.iot.firmware.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.domain.IotFirmwareStatus;
import com.nexus.iot.firmware.domain.bo.IotFirmwareBo;
import com.nexus.iot.firmware.mapper.IotFirmwareMapper;
import com.nexus.iot.firmware.service.IIotFirmwareService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * IoT固件Service业务层处理（OTA MVP）。
 * <p>
 * 关键约定：
 * <ol>
 *   <li><b>状态只能走状态机端点</b>：{@code insertByBo} 强制 DRAFT、{@code updateByBo} 不接受
 *       {@code status}/{@code releaseTime} 写入（BO 无对应字段，服务端再强制一次）；</li>
 *   <li><b>已发布固件冻结</b>：RELEASED 不可编辑（改包会让"设备升级到该版本"失真）、不可删除；</li>
 *   <li><b>唯一键</b>：{@code uk_version(tenant_id, product_key, version)} —— {@code product_key} 为空时
 *       归一化为空串（MySQL 唯一索引对 NULL 不去重，若不归一会产生重复版本）；</li>
 *   <li><b>CAS</b>：所有状态变更都带 {@code eq(status, 读取时的状态)}，0 行即并发冲突并抛异常。</li>
 * </ol>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotFirmwareServiceImpl implements IIotFirmwareService {

    private final IotFirmwareMapper baseMapper;

    /**
     * 分页查询固件列表。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 固件分页结果
     */
    @Override
    public TableDataInfo<IotFirmware> selectPageList(IotFirmwareBo bo, PageQuery pageQuery) {
        Page<IotFirmware> page = baseMapper.selectPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询固件列表（不分页）。
     *
     * @param bo 查询条件
     * @return 固件列表
     */
    @Override
    public List<IotFirmware> selectList(IotFirmwareBo bo) {
        return baseMapper.selectList(buildQueryWrapper(bo));
    }

    /**
     * 查询固件详情。
     *
     * @param firmwareId 固件ID
     * @return 固件实体，不存在返回 null
     */
    @Override
    public IotFirmware selectById(Long firmwareId) {
        return firmwareId == null ? null : baseMapper.selectById(firmwareId);
    }

    /**
     * 登记固件：服务端强制 DRAFT + 版本号唯一校验。
     *
     * @param bo 固件业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotFirmwareBo bo) {
        String productKey = normalizeKey(bo.getProductKey());
        String version = normalizeKey(bo.getVersion());
        requireVersionFree(null, productKey, version);
        IotFirmware entity = new IotFirmware();
        entity.setFirmwareName(bo.getFirmwareName());
        entity.setProductKey(productKey);
        entity.setHardwareModel(normalizeKey(bo.getHardwareModel()));
        entity.setVersion(version);
        entity.setFileOssId(bo.getFileOssId());
        entity.setFileName(bo.getFileName());
        entity.setFileSize(bo.getFileSize() == null ? 0L : bo.getFileSize());
        entity.setChecksumType(normalizeChecksumType(bo.getChecksumType()));
        entity.setChecksum(normalizeChecksum(bo.getChecksum()));
        entity.setChangeLog(bo.getChangeLog());
        entity.setRemark(bo.getRemark());
        // 服务端强制：新建一律草稿，发布时间留空（发布只能走状态机端点）
        entity.setStatus(IotFirmwareStatus.DRAFT);
        entity.setReleaseTime(null);
        try {
            return baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同产品型号下版本号已存在：" + display(productKey) + " / " + version);
        }
    }

    /**
     * 修改固件：仅草稿可改，CAS 防并发。
     *
     * @param bo 固件业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotFirmwareBo bo) {
        if (bo.getFirmwareId() == null) {
            throw new ServiceException("固件ID不能为空");
        }
        IotFirmware exists = baseMapper.selectById(bo.getFirmwareId());
        if (exists == null) {
            throw new ServiceException("固件不存在：" + bo.getFirmwareId());
        }
        if (!IotFirmwareStatus.isEditable(exists.getStatus())) {
            throw new ServiceException("固件当前状态为 " + exists.getStatus()
                + "，仅草稿(DRAFT)可修改（已发布固件请先停用，再新建版本）");
        }
        String productKey = normalizeKey(bo.getProductKey());
        String version = normalizeKey(bo.getVersion());
        requireVersionFree(bo.getFirmwareId(), productKey, version);
        Date now = new Date();
        LambdaUpdateWrapper<IotFirmware> luw = Wrappers.<IotFirmware>lambdaUpdate()
            .eq(IotFirmware::getFirmwareId, bo.getFirmwareId())
            .eq(IotFirmware::getStatus, exists.getStatus())
            .set(IotFirmware::getFirmwareName, bo.getFirmwareName())
            .set(IotFirmware::getProductKey, productKey)
            .set(IotFirmware::getHardwareModel, normalizeKey(bo.getHardwareModel()))
            .set(IotFirmware::getVersion, version)
            .set(IotFirmware::getFileOssId, bo.getFileOssId())
            .set(IotFirmware::getFileName, bo.getFileName())
            .set(IotFirmware::getFileSize, bo.getFileSize() == null ? 0L : bo.getFileSize())
            .set(IotFirmware::getChecksumType, normalizeChecksumType(bo.getChecksumType()))
            .set(IotFirmware::getChecksum, normalizeChecksum(bo.getChecksum()))
            .set(IotFirmware::getChangeLog, bo.getChangeLog())
            .set(IotFirmware::getRemark, bo.getRemark())
            // 服务端强制：状态保持草稿、发布时间保持空
            .set(IotFirmware::getStatus, IotFirmwareStatus.DRAFT)
            .set(IotFirmware::getReleaseTime, null)
            .set(IotFirmware::getUpdateTime, now);
        int rows = baseMapper.update(null, luw);
        if (rows == 0) {
            throw new ServiceException("固件修改失败：状态已被其他操作变更，请刷新后重试");
        }
        return rows;
    }

    /**
     * 批量删除固件：已发布固件拒绝删除。
     *
     * @param ids 固件ID数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        List<Long> idList = List.of(ids);
        List<IotFirmware> list = baseMapper.selectList(Wrappers.<IotFirmware>lambdaQuery()
            .in(IotFirmware::getFirmwareId, idList));
        for (IotFirmware fw : list) {
            if (IotFirmwareStatus.RELEASED.equals(fw.getStatus())) {
                throw new ServiceException("固件[" + fw.getFirmwareName() + " " + fw.getVersion()
                    + "]已发布，请先停用(DISABLED)再删除");
            }
        }
        return baseMapper.delete(Wrappers.<IotFirmware>lambdaQuery().in(IotFirmware::getFirmwareId, idList));
    }

    /**
     * 变更发布状态（发布/停用），CAS。
     *
     * @param firmwareId   固件ID
     * @param targetStatus 目标状态
     * @param remark       变更说明
     * @return 变更后的固件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public IotFirmware changeStatus(Long firmwareId, String targetStatus, String remark) {
        if (firmwareId == null) {
            throw new ServiceException("固件ID不能为空");
        }
        IotFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在：" + firmwareId);
        }
        if (!IotFirmwareStatus.canTransition(fw.getStatus(), targetStatus)) {
            throw new ServiceException("非法的固件状态流转：" + fw.getStatus() + " → " + targetStatus
                + "（合法：DRAFT→RELEASED/DISABLED、RELEASED→DISABLED、DISABLED→RELEASED）");
        }
        if (IotFirmwareStatus.RELEASED.equals(targetStatus)
            && (StringUtils.isBlank(fw.getFileOssId()) || StringUtils.isBlank(fw.getChecksum()))) {
            throw new ServiceException("发布前必须登记固件包(fileOssId)与校验值(checksum)");
        }
        Date now = new Date();
        int rows = baseMapper.update(null, Wrappers.<IotFirmware>lambdaUpdate()
            .eq(IotFirmware::getFirmwareId, firmwareId)
            .eq(IotFirmware::getStatus, fw.getStatus())
            .set(IotFirmware::getStatus, targetStatus)
            .set(IotFirmwareStatus.RELEASED.equals(targetStatus), IotFirmware::getReleaseTime, now)
            .set(StringUtils.isNotBlank(remark), IotFirmware::getRemark, remark)
            .set(IotFirmware::getUpdateTime, now));
        if (rows == 0) {
            throw new ServiceException("固件状态变更失败：状态已被其他操作变更，请刷新后重试");
        }
        log.info("固件状态变更：firmwareId={}, {} → {}", firmwareId, fw.getStatus(), targetStatus);
        return baseMapper.selectById(firmwareId);
    }

    /**
     * 取可用于下发的固件（仅 RELEASED）。
     *
     * @param firmwareId 固件ID
     * @return 固件实体
     */
    @Override
    public IotFirmware requireReleased(Long firmwareId) {
        if (firmwareId == null) {
            throw new ServiceException("固件ID不能为空");
        }
        IotFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在：" + firmwareId);
        }
        if (!IotFirmwareStatus.isReleasable(fw.getStatus())) {
            throw new ServiceException("固件[" + fw.getFirmwareName() + " " + fw.getVersion()
                + "]当前状态为 " + fw.getStatus() + "，仅已发布(RELEASED)固件可下发升级任务");
        }
        return fw;
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * 版本号唯一性校验（{@code uk_version(tenant_id, product_key, version)}）。
     */
    private void requireVersionFree(Long excludeId, String productKey, String version) {
        Long count = baseMapper.selectCount(Wrappers.<IotFirmware>lambdaQuery()
            .eq(IotFirmware::getProductKey, productKey)
            .eq(IotFirmware::getVersion, version)
            .ne(excludeId != null, IotFirmware::getFirmwareId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("同产品型号下版本号已存在：" + display(productKey) + " / " + version);
        }
    }

    private LambdaQueryWrapper<IotFirmware> buildQueryWrapper(IotFirmwareBo bo) {
        LambdaQueryWrapper<IotFirmware> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFirmwareName()), IotFirmware::getFirmwareName, bo.getFirmwareName());
        lqw.eq(StringUtils.isNotBlank(bo.getProductKey()), IotFirmware::getProductKey, bo.getProductKey());
        lqw.like(StringUtils.isNotBlank(bo.getVersion()), IotFirmware::getVersion, bo.getVersion());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), IotFirmware::getStatus, bo.getStatus());
        lqw.orderByDesc(IotFirmware::getFirmwareId);
        return lqw;
    }

    /** 产品型号/硬件型号/版本号归一化：null → 空串（唯一索引对 NULL 不去重）。 */
    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeChecksumType(String type) {
        if (StringUtils.isBlank(type)) {
            return "MD5";
        }
        String upper = type.trim().toUpperCase();
        if (!IotFirmwareStatus.CHECKSUM_TYPES.contains(upper)) {
            throw new ServiceException("校验算法仅支持 MD5/SHA256，当前：" + type);
        }
        return upper;
    }

    private String normalizeChecksum(String checksum) {
        return checksum == null ? "" : checksum.trim().toLowerCase();
    }

    private String display(String productKey) {
        return StringUtils.isBlank(productKey) ? "(不限)" : productKey;
    }
}
