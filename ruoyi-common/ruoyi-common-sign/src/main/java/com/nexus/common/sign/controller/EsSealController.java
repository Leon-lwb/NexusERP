package com.nexus.common.sign.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;
import com.nexus.common.sign.service.IESSealService;
import com.nexus.common.web.core.BaseController;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 电子印章Controller
 *
 * @author nexus
 * @date 2026-09-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/seal")
public class EsSealController extends BaseController {

    private final IESSealService sealService;

    /**
     * 分页查询印章列表
     * <p>权限标识：{@code system:sign:seal:list}</p>
     *
     * @param bo        查询参数（印章类型、名称、状态等）
     * @param pageQuery 分页参数
     * @return 印章分页列表
     */
    @SaCheckPermission("system:sign:seal:list")
    @GetMapping("/list")
    public TableDataInfo<SealVo> list(SealQueryBo bo, PageQuery pageQuery) {
        return sealService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询当前用户在指定业务场景下可用的印章
     * <p><b>无权限标识</b>：本接口供签署弹窗调用，普通审批人不会持有 {@code system:sign:*}
     * 权限，因此只做登录态校验（由 Sa-Token 全局拦截器保证），越权由服务层按
     * 「个人章归属 + 公司章授权窗口」逐条判定。</p>
     *
     * @param bizType 业务场景
     * @return 当前用户可用印章列表
     */
    @GetMapping("/available")
    public R<List<AvailableSealVo>> available(@RequestParam String bizType) {
        return R.ok(sealService.listAvailableSeals(bizType));
    }

    /**
     * 获取印章详细信息
     * <p>权限标识：{@code system:sign:seal:query}</p>
     * <p>返回体<b>不含</b>印章原图 OSS ID（{@code sealOssId}），原图无任何对外接口。</p>
     *
     * @param sealId 印章ID
     * @return 印章详细信息
     */
    @SaCheckPermission("system:sign:seal:query")
    @GetMapping("/{sealId}")
    public R<SealVo> getInfo(@PathVariable Long sealId) {
        return R.ok(sealService.selectById(sealId));
    }

    /**
     * 新增印章
     * <p>权限标识：{@code system:sign:seal:add}</p>
     * <p>日志记录：title="电子印章"，businessType=INSERT</p>
     * <p>校验分组：{@code AddGroup} —— 印章图片相关字段（{@code sealOssId}/{@code imgWidth}/
     * {@code imgHeight}）在此分组下必填。</p>
     *
     * @param bo 印章业务对象
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:seal:add")
    @Log(title = "电子印章", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SealBo bo) {
        sealService.insertByBo(bo);
        return R.ok();
    }

    /**
     * 修改印章
     * <p>权限标识：{@code system:sign:seal:edit}</p>
     * <p>日志记录：title="电子印章"，businessType=UPDATE</p>
     * <p>校验分组：{@code EditGroup} —— 不要求回传印章图片；图片字段留空表示「沿用原章面」。
     * 若传了 {@code sealOssId} 则必须同时给出宽高，由服务层 {@code updateByBo} 兜底校验。</p>
     *
     * @param bo 印章业务对象
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:seal:edit")
    @Log(title = "电子印章", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SealBo bo) {
        return toAjax(sealService.updateByBo(bo));
    }

    /**
     * 删除印章（逻辑删除）
     * <p>权限标识：{@code system:sign:seal:remove}</p>
     * <p>日志记录：title="电子印章"，businessType=DELETE</p>
     *
     * @param sealIds 印章ID数组
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:seal:remove")
    @Log(title = "电子印章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sealIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] sealIds) {
        return toAjax(sealService.deleteByIds(Arrays.asList(sealIds)));
    }

    /**
     * 启停/销毁印章
     * <p>权限标识：{@code system:sign:seal:status}</p>
     * <p>日志记录：title="电子印章"，businessType=UPDATE</p>
     * <p>合法状态流转由服务层校验，销毁为终态、不可再变更。</p>
     *
     * @param sealId 印章ID
     * @param status 目标状态（0草稿 1启用 2停用 3销毁）
     * @return 操作结果
     */
    @SaCheckPermission("system:sign:seal:status")
    @Log(title = "电子印章", businessType = BusinessType.UPDATE)
    @PutMapping("/{sealId}/status/{status}")
    public R<Void> changeStatus(@PathVariable Long sealId,
                                @NotNull(message = "状态不能为空") @PathVariable String status) {
        return toAjax(sealService.changeStatus(sealId, status));
    }

    /**
     * 获取印章预览图
     * <p>权限标识：{@code system:sign:seal:query}</p>
     * <p><b>安全边界</b>：本接口只下发<b>低清 + 水印</b>预览图（{@code previewOssId}）；
     * 印章原图（{@code sealOssId}）<b>没有任何对外接口</b>，绝不经本接口或其它接口下发。
     * 生产上传流程必须生成预览图，仅在历史数据缺失预览图时服务层才按其契约回退原图。</p>
     * <p><b>为何返回 ossId 而不是直接流式输出</b>：本模块为 common 模块，
     * 唯一能把 ossId 解析成字节流的 {@code ISysOssService} 位于 {@code ruoyi-modules/ruoyi-system}，
     * 而该系统模块<b>反向依赖本模块</b>，common → system 会构成 Maven 循环依赖，
     * {@code ruoyi-common-oss} 亦只提供 {@code OssFactory}/{@code OssClient}（无 ossId → 对象键映射）。
     * 故此处仅返回预览图 ossId，由前端经既有的鉴权端点 {@code /resource/oss/download/{ossId}}
     * 取流渲染。</p>
     *
     * @param sealId 印章ID
     * @return 预览图 OSS ID（前端再经 {@code /resource/oss/download/{ossId}} 取图）
     */
    @SaCheckPermission("system:sign:seal:query")
    @GetMapping("/{sealId}/preview")
    public R<Long> preview(@PathVariable Long sealId) {
        return R.ok(sealService.loadPreviewImage(sealId));
    }
}
