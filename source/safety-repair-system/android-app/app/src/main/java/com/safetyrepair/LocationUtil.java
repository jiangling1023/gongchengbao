package com.safetyrepair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 位置解析工具：从设备 location 字段推断楼栋 / 楼层，用于分组与缺陷设备地图。
 *
 * <p>解析规则（按优先级）：
 * <ul>
 *   <li>英文楼层：含 "1F" / "2F" 等 → "1F"。</li>
 *   <li>中文楼层：含 "一楼" / "二楼" / "十楼" 等 → "1F"。</li>
 *   <li>楼栋：含 "A栋" / "B楼" / "1栋" 等 → "A栋"；缺省为 "主楼"。</li>
 * </ul>
 * 仅做轻量文本推断，失败安全（返回空串或默认楼栋），不依赖任何外部库。
 */
public final class LocationUtil {
    private static final Pattern FLOOR_EN = Pattern.compile("(\\d+)\\s*[Ff]");
    private static final String[] CN_NUM = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final String DEFAULT_BUILDING = "主楼";

    private LocationUtil() {
        // 工具类，禁止实例化
    }

    /** 提取楼层标识（如 "1F" / "2F" / "3F"）；无法推断返回空串。 */
    public static String extractFloor(String location) {
        if (location == null) {
            return "";
        }
        String loc = location.trim();
        if (loc.isEmpty()) {
            return "";
        }
        Matcher m = FLOOR_EN.matcher(loc);
        if (m.find()) {
            return m.group(1) + "F";
        }
        for (int i = 1; i <= 10; i++) {
            String cn = (i == 10) ? "十楼" : CN_NUM[i] + "楼";
            if (loc.contains(cn)) {
                return i + "F";
            }
        }
        return "";
    }

    /** 提取楼栋（如 "A栋" / "B栋"）；无则返回默认楼栋。 */
    public static String extractBuilding(String location) {
        if (location == null) {
            return DEFAULT_BUILDING;
        }
        String loc = location.trim();
        Pattern p = Pattern.compile("([A-Za-z0-9])\\s*[栋楼]");
        Matcher m = p.matcher(loc);
        if (m.find()) {
            return m.group(1).toUpperCase() + "栋";
        }
        return DEFAULT_BUILDING;
    }

    /** 分组键：楼栋 + 楼层；无楼层时归入原始 location 或 "未分层"。 */
    public static String groupKey(String location) {
        String b = extractBuilding(location);
        String f = extractFloor(location);
        if (f.isEmpty()) {
            if (location != null && !location.trim().isEmpty()) {
                return location.trim();
            }
            return "未分层";
        }
        return b + " " + f;
    }

    /** 楼层排序权重：数字楼层升序，"未分层" 最大。 */
    public static int floorOrder(String groupKey) {
        if (groupKey == null) {
            return Integer.MAX_VALUE;
        }
        if (groupKey.equals("未分层")) {
            return Integer.MAX_VALUE;
        }
        Matcher m = FLOOR_EN.matcher(groupKey);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception e) {
                return Integer.MAX_VALUE - 1;
            }
        }
        return Integer.MAX_VALUE - 1;
    }
}
