package org.dromara.im.push;

import cn.hutool.core.map.MapUtil;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.im.constants.ImPayloadConstants;

import java.util.Map;

public final class ImEnvelopeHelper {

    private ImEnvelopeHelper() {
    }

    public static String userNotification(long notificationId, String category, String title, String contentPreview) {
        Map<String, Object> payload = MapUtil.<String, Object>builder()
            .put("notificationId", notificationId)
            .put("category", category)
            .put("title", title)
            .put("contentPreview", contentPreview)
            .build();
        return envelope(ImPayloadConstants.TYPE_USER_NOTIFICATION, payload);
    }

    public static String imMessage(Map<String, Object> payload) {
        return envelope(ImPayloadConstants.TYPE_IM_MESSAGE, payload);
    }

    private static String envelope(String type, Map<String, Object> payload) {
        Map<String, Object> root = MapUtil.<String, Object>builder()
            .put("v", ImPayloadConstants.ENVELOPE_VERSION)
            .put("type", type)
            .put("ts", System.currentTimeMillis())
            .put("payload", payload)
            .build();
        return JsonUtils.toJsonString(root);
    }
}
