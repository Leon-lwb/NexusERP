package com.nexus.mes.release.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.release.domain.ReleasePackage;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES客户定制化发布包 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface ReleasePackageMapper extends BaseMapperPlus<ReleasePackage, ReleasePackageVo> {
}
