package com.nexus.system.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysDicAreaMapper extends BaseMapperPlus<SysDicArea, SysDicAreaVo> {

    List<SysDicArea> selectProvinces();

    List<SysDicArea> selectNextLevel(@Param("parentAreaId") Long parentAreaId);

    /**
     * 统计指定行政区划代码的占用情况，<b>包含已逻辑删除的行</b>。
     * <p>用于唯一性校验：{@code @TableLogic} 生效后内置 {@code exists} 只统计未删除行，
     * 会让"删除后重建同码"漏过校验，产生重复 area_id。</p>
     *
     * @param areaId      行政区划代码
     * @param excludePkid 需排除的主键（新增时传 -1）
     * @return 占用行数
     */
    @Select("select count(1) from sys_dic_area where area_id = #{areaId} and pkid <> #{excludePkid}")
    long countByAreaIdIncludeDeleted(@Param("areaId") Long areaId, @Param("excludePkid") Long excludePkid);
}
