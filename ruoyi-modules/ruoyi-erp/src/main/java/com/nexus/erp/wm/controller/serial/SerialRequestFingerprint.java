package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.*;

/**
 * 原任务指纹包含版本、数量、源单头及SN；对象键和SN顺序不影响合法重放。
 */
final class SerialRequestFingerprint {
    private SerialRequestFingerprint() {}

    static String of(String uri, JsonNode body) {
        return WmSerialBusinessStore.hash(uri + "\n" + canonical(body, "").toString());
    }

    static JsonNode canonical(JsonNode node, String field) {
        if (node.isObject()) {
            var copy = JsonNodeFactory.instance.objectNode();
            var keys = new TreeSet<String>();
            node.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                copy.set(key, canonical(node.get(key), key));
            }
            return copy;
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(value -> values.add(canonical(value, field)));
            if ("snItems".equals(field)) {
                values.sort(Comparator.comparing(value -> value.path("identityId").asText()));
            }
            var copy = JsonNodeFactory.instance.arrayNode();
            values.forEach(copy::add);
            return copy;
        }
        return node;
    }
}
