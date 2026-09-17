package com.nexus.common.sign.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;

import java.util.List;

/**
 * 印章服务。
 *
 * @author nexus
 */
public interface IESSealService {

    /**
     * 分页查询印章
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<SealVo> selectPageList(SealQueryBo bo, PageQuery pageQuery);

    /**
     * 查询印章列表
     *
     * @param bo 查询条件
     * @return 印章列表
     */
    List<SealVo> selectList(SealQueryBo bo);

    /**
     * 按ID查询印章
     *
     * @param sealId 印章ID
     * @return 印章
     */
    SealVo selectById(Long sealId);

    /**
     * 新增印章
     *
     * @param bo 印章入参
     * @return 新增的印章ID
     */
    Long insertByBo(SealBo bo);

    /**
     * 修改印章
     *
     * @param bo 印章入参
     * @return 影响行数
     */
    int updateByBo(SealBo bo);

    /**
     * 批量删除印章（逻辑删除）
     *
     * @param sealIds 印章ID集合
     * @return 影响行数
     */
    int deleteByIds(List<Long> sealIds);

    /**
     * 启停/销毁印章
     *
     * @param sealId 印章ID
     * @param status 目标状态（0草稿 1启用 2停用 3销毁）
     * @return 影响行数
     */
    int changeStatus(Long sealId, String status);

    /**
     * 查询当前用户在指定业务场景下可用的印章
     *
     * @param bizType 业务场景
     * @return 可用印章列表
     */
    List<AvailableSealVo> listAvailableSeals(String bizType);

    /**
     * 解析印章预览图（低清 + 水印）的 OSS ID —— 前端唯一可获取的图像入口。
     *
     * <p><b>契约（P2a-1 起）</b>：只返回<b>预览图</b> ossId，
     * <b>绝不回退原始章面</b>（{@code sealOssId}）。历史数据（P2a-1 之前落库、
     * 或上次生成失败导致 {@code previewOssId} 为空）会在本方法内<b>惰性补生成</b>：
     * 读原图 → 渲染低清水印图 → 写入对象存储 → 回写 {@code preview_oss_id}。
     * 补生成失败时抛 {@code ServiceException}（宁可报错，也不把高精度原图下发给前端 ——
     * 那正是「印章防滥用」要防的事）。</p>
     *
     * <p><b>返回值说明</b>：本模块为 common 模块，无法依赖 {@code ruoyi-modules/ruoyi-system}
     * 的 {@code ISysOssService}（唯一提供 ossId → 字节/流的服务），故本方法只做
     * 「印章校验 + 预览图准备」，返回 ossId 交由调用方（Controller）用 {@code ISysOssService}
     * （{@code download(ossId, response)}，或 {@code getById} + {@code OssFactory}
     * 自行取流以正确设置 Content-Type）完成流式输出。</p>
     *
     * @param sealId 印章ID
     * @return 预览图 OSS ID（保证不是原图）
     */
    Long loadPreviewImage(Long sealId);
}
