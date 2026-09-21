package com.nexus.iot.device.group.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import io.github.linpeilie.Converter;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.group.domain.IotDeviceTag;
import com.nexus.iot.device.group.domain.IotDeviceTagRel;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBindBo;
import com.nexus.iot.device.group.domain.bo.IotDeviceTagBo;
import com.nexus.iot.device.group.domain.vo.IotDeviceTagVo;
import com.nexus.iot.device.group.mapper.IotDeviceTagMapper;
import com.nexus.iot.device.group.mapper.IotDeviceTagRelMapper;
import com.nexus.iot.device.group.service.IIotDeviceTagService;
import com.nexus.iot.device.mapper.IotDeviceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IoT设备标签Service业务层处理。
 * <p>
 * 标签为多值维度，设备-标签关系在 {@link IotDeviceTagRel}（N:N，唯一键幂等）；
 * 删除标签时级联清理关系，避免脏关系残留。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("iot")
@Service
public class IotDeviceTagServiceImpl implements IIotDeviceTagService {

    private final IotDeviceTagMapper baseMapper;
    private final IotDeviceTagRelMapper relMapper;
    private final IotDeviceMapper deviceMapper;
    /**
     * mapstruct-plus 转换器（注入而非静态 {@code MapstructUtils}：后者静态初始化依赖 Spring 容器，单测无法打桩）。
     */
    private final Converter converter;

    /**
     * 查询标签列表。
     *
     * @param bo 查询条件
     * @return 标签列表
     */
    @Override
    public List<IotDeviceTagVo> selectList(IotDeviceTagBo bo) {
        LambdaQueryWrapper<IotDeviceTag> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getTagName()), IotDeviceTag::getTagName, bo.getTagName());
        lqw.orderByAsc(IotDeviceTag::getTagId);
        List<IotDeviceTagVo> list = baseMapper.selectVoList(lqw);
        fillDeviceCount(list);
        return list;
    }

    /**
     * 查询标签详情。
     *
     * @param tagId 标签ID
     * @return 标签VO，不存在返回 null
     */
    @Override
    public IotDeviceTagVo selectById(Long tagId) {
        if (tagId == null) {
            return null;
        }
        IotDeviceTagVo vo = baseMapper.selectVoById(tagId);
        if (vo != null) {
            fillDeviceCount(List.of(vo));
        }
        return vo;
    }

    /**
     * 新增标签。
     *
     * @param bo 标签业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(IotDeviceTagBo bo) {
        IotDeviceTag entity = converter.convert(bo, IotDeviceTag.class);
        if (entity == null) {
            throw new ServiceException("标签数据转换失败");
        }
        if (StringUtils.isBlank(entity.getTagColor())) {
            entity.setTagColor("#409EFF");
        }
        try {
            return baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("标签名称已存在：" + bo.getTagName());
        }
    }

    /**
     * 修改标签。
     *
     * @param bo 标签业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(IotDeviceTagBo bo) {
        if (bo.getTagId() == null) {
            throw new ServiceException("标签ID不能为空");
        }
        if (baseMapper.selectById(bo.getTagId()) == null) {
            throw new ServiceException("标签不存在：" + bo.getTagId());
        }
        IotDeviceTag entity = converter.convert(bo, IotDeviceTag.class);
        try {
            return baseMapper.updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("标签名称已存在：" + bo.getTagName());
        }
    }

    /**
     * 批量删除标签（级联清理关系）。
     *
     * @param ids 标签ID数组
     * @return 删除影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        List<Long> tagIds = List.of(ids);
        int rels = relMapper.delete(Wrappers.<IotDeviceTagRel>lambdaQuery()
            .in(IotDeviceTagRel::getTagId, tagIds));
        int rows = baseMapper.deleteBatchIds(tagIds);
        log.info("删除标签：tags={}, 级联清理关系={} 条", tagIds, rels);
        return rows;
    }

    /**
     * 批量绑定/解绑标签（幂等）。
     *
     * @param bo 请求体
     * @return 实际变更关系行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindTags(IotDeviceTagBindBo bo) {
        List<Long> deviceIds = distinct(bo.getDeviceIds());
        List<Long> tagIds = distinct(bo.getTagIds());
        if (deviceIds.isEmpty() || tagIds.isEmpty()) {
            throw new ServiceException("设备ID与标签ID均不能为空");
        }
        // 标签存在性校验（多租户插件保证不跨租户）
        List<IotDeviceTag> tags = baseMapper.selectBatchIds(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ServiceException("部分标签不存在或无权限访问，请刷新后重试");
        }
        List<IotDevice> devices = deviceMapper.selectBatchIds(deviceIds);
        if (devices.size() != deviceIds.size()) {
            throw new ServiceException("部分设备不存在或无权限访问，请刷新后重试");
        }
        String tenantId = TenantHelper.getTenantId();
        boolean remove = Boolean.TRUE.equals(bo.getRemove());
        if (remove) {
            int rows = relMapper.delete(Wrappers.<IotDeviceTagRel>lambdaQuery()
                .in(IotDeviceTagRel::getDeviceId, deviceIds)
                .in(IotDeviceTagRel::getTagId, tagIds));
            log.info("设备解绑标签：deviceIds={}, tagIds={}, rows={}", deviceIds, tagIds, rows);
            return rows;
        }
        List<IotDeviceTagRel> exists = relMapper.selectList(Wrappers.<IotDeviceTagRel>lambdaQuery()
            .in(IotDeviceTagRel::getDeviceId, deviceIds)
            .in(IotDeviceTagRel::getTagId, tagIds));
        int rows = 0;
        Date now = new Date();
        for (Long deviceId : deviceIds) {
            for (Long tagId : tagIds) {
                if (exists.stream().anyMatch(r -> deviceId.equals(r.getDeviceId()) && tagId.equals(r.getTagId()))) {
                    continue;
                }
                IotDeviceTagRel rel = new IotDeviceTagRel();
                rel.setTenantId(tenantId);
                rel.setDeviceId(deviceId);
                rel.setTagId(tagId);
                rel.setCreateTime(now);
                rows += relMapper.insert(rel);
            }
        }
        log.info("设备绑定标签：deviceIds={}, tagIds={}, inserted={}", deviceIds, tagIds, rows);
        return rows;
    }

    /**
     * 查询某设备已绑定的标签ID集合。
     *
     * @param deviceId 设备ID
     * @return 标签ID列表
     */
    @Override
    public List<Long> selectTagIdsByDevice(Long deviceId) {
        if (deviceId == null) {
            return List.of();
        }
        return relMapper.selectTagIdsByDevice(TenantHelper.getTenantId(), deviceId);
    }

    // ------------------------------------------------------------------ 内部实现

    private void fillDeviceCount(List<IotDeviceTagVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (IotDeviceTagVo vo : list) {
            if (vo.getTagId() != null) {
                ids.add(vo.getTagId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> countMap = new HashMap<>();
        try {
            List<Map<String, Object>> rows = relMapper.countDevicesByTagIds(TenantHelper.getTenantId(), ids);
            for (Map<String, Object> row : rows) {
                Object tid = row.get("tagId");
                Object cnt = row.get("deviceCount");
                if (tid instanceof Number n && cnt instanceof Number c) {
                    countMap.put(n.longValue(), c.longValue());
                }
            }
        } catch (Exception e) {
            log.warn("统计标签设备数失败（不影响列表返回）：{}", e.getMessage());
        }
        for (IotDeviceTagVo vo : list) {
            vo.setDeviceCount(countMap.getOrDefault(vo.getTagId(), 0L));
        }
    }

    private List<Long> distinct(List<Long> ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (Long id : ids) {
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
