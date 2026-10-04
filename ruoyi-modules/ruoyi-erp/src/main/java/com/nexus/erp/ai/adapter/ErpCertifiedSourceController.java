package com.nexus.erp.ai.adapter;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import java.util.Objects;

/** 新测试范围实际创建和完整来源预览端点，旧物料不存在补出生接口。 */
@RestController
@RequestMapping("/erp/ai/certified-source")
public class ErpCertifiedSourceController {
    private final ErpCertifiedSourceBirthService births;
    private final ErpCertifiedSourcePreviewService previews;
    public ErpCertifiedSourceController(ErpCertifiedSourceBirthService births,ErpCertifiedSourcePreviewService previews){
        this.births=Objects.requireNonNull(births);this.previews=Objects.requireNonNull(previews);
    }
    @PostMapping("/materials")
    @SaCheckPermission("bd:material:add")
    public R<ErpCertifiedSourceBirthService.CreatedMaterial> create(@RequestBody ErpCertifiedSourceBirthService.CreateMaterial request){
        return R.ok(births.create(request));
    }
    @PostMapping("/preview")
    @SaCheckPermission("pu:requisition:add")
    public R<ErpCertifiedSourcePreviewService.SourcePreview> preview(@RequestBody ErpCertifiedSourcePreviewService.PreviewRequest request){
        return R.ok(previews.preview(request));
    }
}
