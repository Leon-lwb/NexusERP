package com.nexus.common.sign.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;

import java.util.List;

/**
 * 用章授权服务。
 *
 * @author nexus
 */
public interface IESSealGrantService {

    TableDataInfo<SealGrantVo> selectPageList(SealGrantBo bo, PageQuery pageQuery);

    List<SealGrantVo> selectListBySeal(Long sealId);

    Long insertByBo(SealGrantBo bo);

    int updateByBo(SealGrantBo bo);

    int deleteByIds(List<Long> grantIds);
}
