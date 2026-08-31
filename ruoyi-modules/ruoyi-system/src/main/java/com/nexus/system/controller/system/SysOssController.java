package com.nexus.system.controller.system;


import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.validate.QueryGroup;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysOssBo;
import com.nexus.system.domain.vo.SysOssUploadVo;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * OSS 对象存储（文件上传）Controller
 * <p>提供 OSS 对象的分页查询、按 ID 批量查询、文件上传、下载及删除等接口。
 * 属于系统管理（System）模块中的资源管理部分。</p>
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/oss")
public class SysOssController extends BaseController {

    /** OSS 对象存储业务服务，提供分页、上传、下载与删除等能力。 */
    private final ISysOssService ossService;

    /**
     * 分页查询 OSS 对象存储列表。
     *
     * @param bo        对象存储查询条件（文件名、分类等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return OSS 对象分页结果
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/list")
    public TableDataInfo<SysOssVo> list(@Validated(QueryGroup.class) SysOssBo bo, PageQuery pageQuery) {
        return ossService.queryPageList(bo, pageQuery);
    }

    /**
     * 根据 ID 串批量查询 OSS 对象。
     *
     * @param ossIds OSS 对象 ID 数组
     * @return OSS 对象列表
     */
    @SaCheckPermission("system:oss:query")
    @GetMapping("/listByIds/{ossIds}")
    public R<List<SysOssVo>> listByIds(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ossIds) {
        List<SysOssVo> list = ossService.listByIds(Arrays.asList(ossIds));
        return R.ok(list);
    }

    /**
     * 上传文件到 OSS 对象存储，返回可访问的文件地址。
     *
     * @param file 待上传的文件
     * @return 上传结果，含文件 URL、原始文件名与 OSS 对象 ID
     */
    @SaCheckPermission("system:oss:upload")
    @Log(title = "OSS对象存储", businessType = BusinessType.INSERT)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<SysOssUploadVo> upload(@RequestPart("file") MultipartFile file) {
        SysOssVo oss = ossService.upload(file);
        SysOssUploadVo uploadVo = new SysOssUploadVo();
        uploadVo.setUrl(oss.getUrl());
        uploadVo.setFileName(oss.getOriginalName());
        uploadVo.setOssId(oss.getOssId().toString());
        return R.ok(uploadVo);
    }

    /**
     * 下载指定 OSS 对象到响应流。
     *
     * @param ossId    OSS 对象 ID
     * @param response HTTP 响应，用于写入文件流
     * @throws IOException 写出响应流异常时抛出
     */
    @SaCheckPermission("system:oss:download")
    @GetMapping("/download/{ossId}")
    public void download(@PathVariable Long ossId, HttpServletResponse response) throws IOException {
        ossService.download(ossId, response);
    }

    /**
     * 批量删除 OSS 对象存储。
     *
     * @param ossIds OSS 对象 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:oss:remove")
    @Log(title = "OSS对象存储", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ossIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ossIds) {
        return toAjax(ossService.deleteWithValidByIds(List.of(ossIds), true));
    }

}
