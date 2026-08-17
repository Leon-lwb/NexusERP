package com.nexus.mes.flash.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.flash.domain.MesContentFile;
import com.nexus.mes.flash.domain.bo.MesContentFileBo;
import com.nexus.mes.flash.mapper.MesContentFileMapper;
import com.nexus.mes.flash.service.IMesContentFileService;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;

/**
 * 刷写内容档案(配置文件) Service 实现
 * <p>配置文件 CRUD + 发布(DRAFT→RELEASED); 复用固件档案范式(简化, P2 不接 Flowable, 管理员直接发布)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class MesContentFileServiceImpl implements IMesContentFileService {

    private final MesContentFileMapper baseMapper;
    private final AutoCodeUtil autoCodeUtil;

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_RELEASED = "RELEASED";

    @Override
    public TableDataInfo<MesContentFile> selectPageList(MesContentFileBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<MesContentFile> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getContentName()), MesContentFile::getContentName, bo.getContentName())
            .eq(bo.getMaterialId() != null, MesContentFile::getMaterialId, bo.getMaterialId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), MesContentFile::getStatus, bo.getStatus())
            .orderByDesc(MesContentFile::getCreateTime);
        Page<MesContentFile> page = baseMapper.selectPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public MesContentFile selectById(Long contentFileId) {
        return baseMapper.selectById(contentFileId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(MesContentFileBo bo) {
        MesContentFile cf = new MesContentFile();
        cf.setContentCode(autoCodeUtil.genSerialCode("MES_CONFIG_CODE", null));
        cf.setContentName(bo.getContentName());
        cf.setContentType(StringUtils.isBlank(bo.getContentType()) ? "CONFIG_FILE" : bo.getContentType());
        cf.setMaterialId(bo.getMaterialId());
        cf.setMaterialCode(bo.getMaterialCode());
        cf.setMaterialName(bo.getMaterialName());
        cf.setVersion(bo.getVersion());
        cf.setOssId(bo.getOssId());
        cf.setFileMd5(bo.getFileMd5());
        cf.setFileSize(bo.getFileSize());
        cf.setReleaseNote(bo.getReleaseNote());
        cf.setStatus(STATUS_DRAFT);
        cf.setIsDefault("0");
        cf.setRemark(bo.getRemark());
        baseMapper.insert(cf);
        log.info("配置文件新增: id={}, code={}", cf.getContentFileId(), cf.getContentCode());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(MesContentFileBo bo) {
        MesContentFile cf = baseMapper.selectById(bo.getContentFileId());
        if (cf == null) {
            throw new ServiceException("配置文件不存在: " + bo.getContentFileId());
        }
        if (!STATUS_DRAFT.equals(cf.getStatus())) {
            throw new ServiceException("仅草稿(DRAFT)状态可编辑, 当前: " + cf.getStatus());
        }
        cf.setContentName(bo.getContentName());
        cf.setMaterialId(bo.getMaterialId());
        cf.setMaterialCode(bo.getMaterialCode());
        cf.setMaterialName(bo.getMaterialName());
        cf.setVersion(bo.getVersion());
        cf.setOssId(bo.getOssId());
        cf.setFileMd5(bo.getFileMd5());
        cf.setFileSize(bo.getFileSize());
        cf.setReleaseNote(bo.getReleaseNote());
        cf.setRemark(bo.getRemark());
        baseMapper.updateById(cf);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        Long nonDraft = baseMapper.selectCount(Wrappers.<MesContentFile>lambdaQuery()
            .in(MesContentFile::getContentFileId, Arrays.asList(ids))
            .ne(MesContentFile::getStatus, STATUS_DRAFT));
        if (nonDraft != null && nonDraft > 0) {
            throw new ServiceException("仅草稿(DRAFT)状态可删除");
        }
        baseMapper.deleteByIds(Arrays.asList(ids));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean release(Long contentFileId) {
        MesContentFile cf = baseMapper.selectById(contentFileId);
        if (cf == null) {
            throw new ServiceException("配置文件不存在: " + contentFileId);
        }
        if (!STATUS_DRAFT.equals(cf.getStatus())) {
            throw new ServiceException("仅草稿(DRAFT)状态可发布, 当前: " + cf.getStatus());
        }
        if (cf.getOssId() == null) {
            throw new ServiceException("发布前请先上传配置文件");
        }
        int rows = baseMapper.update(null, Wrappers.<MesContentFile>lambdaUpdate()
            .set(MesContentFile::getStatus, STATUS_RELEASED)
            .set(MesContentFile::getPublishedBy, LoginHelper.getUserId())
            .set(MesContentFile::getPublishTime, new Date())
            .eq(MesContentFile::getContentFileId, contentFileId)
            .eq(MesContentFile::getStatus, STATUS_DRAFT));
        if (rows == 0) {
            throw new ServiceException("配置文件状态已变更, 发布失败");
        }
        log.info("配置文件发布: id={}, code={}", contentFileId, cf.getContentCode());
        return true;
    }
}
