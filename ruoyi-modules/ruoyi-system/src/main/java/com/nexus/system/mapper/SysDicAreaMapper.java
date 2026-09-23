package com.nexus.system.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface SysDicAreaMapper extends BaseMapperPlus<SysDicArea, SysDicAreaVo> {

    List<SysDicArea> selectProvinces();

    List<SysDicArea> selectNextLevel(@Param("parentAreaId") Long parentAreaId);

    /**
     * 查询给定行政区划代码中<b>确实存在下级</b>的那些。
     * <p>用于回填 {@code SysDicAreaVo.hasChildren}：前端懒加载表格与 el-tree-select 靠它决定是否
     * 渲染展开箭头，靠"层级 &lt; 5"猜会多出大量点了返回空列表的假箭头。</p>
     *
     * <p><b>为什么手写 SQL 而不是 LambdaQueryWrapper</b>：① 需要 {@code group by} 只取一列，
     * Lambda 写法的 {@code select(...)} 会在构造时立刻解析列名，纯 Mockito 单测没有 MyBatis 上下文
     * （TableInfo 未注册）会直接抛 {@code can not find lambda cache}；② 手写 SQL 可以显式写
     * {@code del_flag = '0'}——手写语句不受 {@code @TableLogic} 改写，与
     * {@link #selectProvinces()} 的既有约定一致。</p>
     *
     * @param areaIds 待判定的行政区划代码（调用方保证非空）
     * @return 存在下级的行政区划代码（走 {@code idx_parent_area_id}，34 个省级实测 0.6ms）
     */
    List<Long> selectParentAreaIdsHavingChildren(@Param("areaIds") Collection<Long> areaIds);

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
