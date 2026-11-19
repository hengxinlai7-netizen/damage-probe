package com.hengx.damageprobe.probe;

/** 一条效果/修饰符变动记录（未格式化计数）。 */
public final class ProbeEvent {

    public enum Category {
        EFFECT("效果探针", "效果"),
        MODIFIER("修饰符探针", "修饰符");

        public final String prefix;
        public final String label;

        Category(String prefix, String label) {
            this.prefix = prefix;
            this.label = label;
        }
    }

    public enum Result {
        APPLIED("已施加"),
        REJECTED("被拒绝"),
        REMOVED("已移除"),
        ALL_REMOVED("全部移除"),
        NONE("未发生"),
        UNKNOWN("未知");

        public final String label;

        Result(String label) {
            this.label = label;
        }
    }

    public final Category category;
    public final long tick;
    public final int targetId;
    public final int sourceId;
    public final String key;
    public final String text;
    public final Result result;
    public final boolean playerRelated;

    public ProbeEvent(Category category, long tick, int targetId, int sourceId,
                      String key, String text, Result result, boolean playerRelated) {
        this.category = category;
        this.tick = tick;
        this.targetId = targetId;
        this.sourceId = sourceId;
        this.key = key;
        this.text = text;
        this.result = result;
        this.playerRelated = playerRelated;
    }
}
