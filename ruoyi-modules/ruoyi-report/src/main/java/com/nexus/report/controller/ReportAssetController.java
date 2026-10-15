package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.asset.ReportAssetService;
import com.nexus.report.extension.ReportResourceStore;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/** 私有图片经过当前身份、资源ACL和checksum后代理输出。 */
@RestController
@RequestMapping("/report/assets")
public final class ReportAssetController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportAssetService service;

    /**
     * 注入正常登录身份和私有资产服务。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     */
    public ReportAssetController(ReportRequestActor actors, ReportAssetService service) {
        this.actors = actors;
        this.service = service;
    }

    /**
     * 查询当前用户可管理目录。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:asset:list")
    @GetMapping
    public R<List<ReportResourceStore.Draft>> list() {
        return R.ok(service.list(actors.current()));
    }

    /**
     * 显式上传PNG或JPEG并发布固定私有版本。
     *
     * @param code 当前契约的code配置或状态
     * @param name 契约名称
     * @param file 当前契约的file配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:asset:manage")
    @PostMapping
    public R<ReportResourceStore.Version> upload(
            @RequestParam String code,
            @RequestParam String name,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        if (file.getSize() > 5 * 1024 * 1024) throw com.nexus.report.domain.ReportFault.capacity();
        return R.ok(service.upload(actors.current(), code, name, file.getBytes()));
    }

    /**
     * 带登录认证代理读取，不提供外链。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param response 当前契约的response配置或状态
     */
    @SaCheckPermission("report:runtime:preview")
    @GetMapping("/versions/{id}/file")
    public void file(@PathVariable String id, HttpServletResponse response) throws IOException {
        byte[] bytes = service.bytes(actors.current(), id);
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    /**
     * 资源状态变更携带revision，旧发布保持不可改写。
     *
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     */
    public record State(long revision, String state) {}

    /**
     * 显式下线使后续运行和已保存计划读取失效。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:asset:manage")
    @PostMapping("/{id}/state")
    public R<ReportResourceStore.Draft> state(@PathVariable String id, @RequestBody State body) {
        return R.ok(service.state(actors.current(), id, body.revision(), body.state()));
    }
}
