package com.nexus.station.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.ncr.domain.NcrDefectCode;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 缺陷代码只读查询 Mapper (ncr_defect_code, ry-mes 库, 工位机不良代码字典 C9)
 *
 * @author Nexus
 */
@DS("mes")
@Mapper
public interface StationDefectCodeMapper extends BaseMapperPlus<NcrDefectCode, NcrDefectCode> {

    /**
     * 查询启用的三级缺陷代码(工位机不良代码字典, 按 sort_order 升序)。
     * enabled 取值 Y/N(建表默认 Y)。
     */
    default List<NcrDefectCode> selectEnabledLeafCodes() {
        return selectList(Wrappers.<NcrDefectCode>lambdaQuery()
            .eq(NcrDefectCode::getLevel, 3)
            .eq(NcrDefectCode::getEnabled, "Y")
            .orderByAsc(NcrDefectCode::getSortOrder));
    }
}
