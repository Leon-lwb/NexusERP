package com.nexus.report.print;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import java.util.*;

/** 固定打印能力的字段、媒体和纸张范围校验。 */
public final class PrintPlanner {
    /**
     * 明确方向后得到实际纸张，旧定义没有打印配置时原样返回。
     *
     * @param dsl 不可变的报表定义
     * @return 当前受控处理的固定结果
     */
    public ReportDsl.Page paper(ReportDsl dsl) {
        var page = dsl.page();
        if (dsl.print() == null || dsl.print().orientation().equals("PORTRAIT")) return page;
        if (!dsl.print().orientation().equals("LANDSCAPE")) throw ReportFault.invalid("纸张方向无效");
        return new ReportDsl.Page(
                page.height(),
                page.width(),
                page.marginTop(),
                page.marginRight(),
                page.marginBottom(),
                page.marginLeft(),
                page.rowHeight());
    }

    /**
     * 返回需要保存和鉴权的额外字段；媒体不得越过页面。
     *
     * @param dsl 不可变的报表定义
     * @param dataset 当前契约的dataset配置或状态
     * @return 当前受控处理的固定结果
     */
    public Set<String> validate(ReportDsl dsl, DatasetDescriptor dataset) {
        if (dsl.print() == null) return Set.of();
        var print = dsl.print();
        var paper = paper(dsl);
        if (dsl.kind() != ReportDsl.Kind.DOCUMENT
                || print.groupBy().size() > 3
                || new HashSet<>(print.groupBy()).size() != print.groupBy().size()
                || print.media().size() > 6) throw ReportFault.invalid("打印扩展只适用于单据，最多3个复合分组键和6个媒体");
        var required = new LinkedHashSet<String>();
        for (String field : print.groupBy()) {
            dataset.field(field);
            required.add(field);
        }
        for (var media : print.media()) {
            if (!Set.of("IMAGE", "QR", "CODE128").contains(media.kind())
                    || !Double.isFinite(media.x() + media.y() + media.width() + media.height())
                    || media.x() < paper.marginLeft()
                    || media.y() < paper.marginTop()
                    || media.width() < 16
                    || media.height() < 16
                    || media.x() + media.width() > paper.width() - paper.marginRight()
                    || media.y() + media.height() > paper.height() - paper.marginBottom())
                throw ReportFault.invalid("媒体类型、坐标或尺寸无效");
            if (media.kind().equals("IMAGE")) {
                if (media.assetVersionId() == null
                        || !media.assetVersionId().matches("[1-9][0-9]{0,18}")
                        || media.field() != null) throw ReportFault.invalid("图片必须绑定明确私有版本");
            } else {
                if (media.assetVersionId() != null || media.field() == null)
                    throw ReportFault.invalid("条码必须绑定批准字段");
                var field = dataset.field(media.field());
                if (!Set.of(ValueType.STRING, ValueType.ID).contains(field.type())
                        || !field.name()
                                .toLowerCase(Locale.ROOT)
                                .matches(".*(code|no|number|id|sn)$"))
                    throw ReportFault.invalid("条码只能绑定批准业务编码字段");
                if (media.kind().equals("QR") && Math.abs(media.width() - media.height()) > .01)
                    throw ReportFault.invalid("二维码打印区域必须为正方形");
                required.add(media.field());
            }
        }
        for (var summary : dsl.summaries())
            if (!summary.function().startsWith("COUNT")
                    && !print.groupBy().isEmpty()
                    && !print.groupBy()
                            .containsAll(dataset.field(summary.field()).aggregationKeys()))
                throw ReportFault.invalid("打印分组小计必须保留单位或币种口径");
        return Collections.unmodifiableSet(required);
    }
}
