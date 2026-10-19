package com.nexus.erp.wm.domain.bo;

import com.nexus.common.trace.SnItem;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** detailVersion为服务端摘要字符串；revision是数据库过账协议整数修订。 */
@Data
public class WmSerialSelectionBo {
    @NotBlank private String sourceDocType;
    @NotNull private Long sourceDocId;
    @NotNull private Long lineId;
    @NotBlank @Size(max=128) private String commandId;
    @NotBlank private String detailVersion;
    @Min(1) private long revision=1;
    @Min(1) private long policyVersion;
    @NotEmpty private List<SnItem> snItems;
}
