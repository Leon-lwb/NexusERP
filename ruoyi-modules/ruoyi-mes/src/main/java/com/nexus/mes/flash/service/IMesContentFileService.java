package com.nexus.mes.flash.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.MesContentFile;
import com.nexus.mes.flash.domain.bo.MesContentFileBo;

/**
 * 刷写内容档案(配置文件) Service
 *
 * @author Nexus
 */
public interface IMesContentFileService {

    TableDataInfo<MesContentFile> selectPageList(MesContentFileBo bo, PageQuery pageQuery);

    MesContentFile selectById(Long contentFileId);

    Boolean insertByBo(MesContentFileBo bo);

    Boolean updateByBo(MesContentFileBo bo);

    Boolean deleteByIds(Long[] ids);

    /** 发布: DRAFT -> RELEASED (记录发布人/时间) */
    Boolean release(Long contentFileId);
}
