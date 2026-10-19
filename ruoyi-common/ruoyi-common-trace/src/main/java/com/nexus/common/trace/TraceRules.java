package com.nexus.common.trace;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
/** 所有终端共用的单件数量和金额守恒规则；业务来源仍须在所属库事务中核验。 */
public final class TraceRules {
    private TraceRules() { }
    public static void validateSerialItems(TrackingPolicy policy, BigDecimal quantity, List<SnItem> items) {
        Objects.requireNonNull(policy,"追踪策略不能为空");
        if (!policy.serialManaged()) {
            if (items != null && !items.isEmpty()) throw new IllegalArgumentException("非SN物料不能提交单件库存明细");
            return;
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("SN物料基本单位数量必须为正整数");
        if (items == null || quantity.compareTo(BigDecimal.valueOf(items.size())) != 0)
            throw new IllegalArgumentException("SN明细件数必须等于本次基本单位数量");
        Set<Long> ids=new HashSet<>();
        for (SnItem item:items) {
            if (item == null || !ids.add(item.identityId())) throw new IllegalArgumentException("SN明细为空或实物身份重复");
            if (policy.batchManaged() && item.batchId() == null) throw new IllegalArgumentException("批次SN物料缺少批次身份");
        }
    }
    /** 按稳定身份排序分配四位金额尾差，合计严格等于原金额。 */
    public static Map<Long,BigDecimal> allocateAmount(BigDecimal total,List<Long> identities) {
        if (total == null || total.signum() < 0 || total.stripTrailingZeros().scale() > 4
            || identities == null || identities.isEmpty()) throw new IllegalArgumentException("金额或实物集合无效");
        TreeSet<Long> sorted=new TreeSet<>();
        for (Long id:identities) if (id == null || id <= 0 || !sorted.add(id)) throw new IllegalArgumentException("实物身份无效或重复");
        BigDecimal rounded=total.setScale(4,RoundingMode.UNNECESSARY);
        BigDecimal each=rounded.divide(BigDecimal.valueOf(sorted.size()),4,RoundingMode.DOWN);
        int ticks=rounded.subtract(each.multiply(BigDecimal.valueOf(sorted.size()))).movePointRight(4).intValueExact();
        LinkedHashMap<Long,BigDecimal> result=new LinkedHashMap<>();
        for (Long id:sorted) result.put(id,each.add(ticks-- > 0 ? new BigDecimal("0.0001") : BigDecimal.ZERO));
        return Collections.unmodifiableMap(result);
    }
}
