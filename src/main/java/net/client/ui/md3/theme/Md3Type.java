package net.client.ui.md3.theme;

/** Material 3 type scale, adapted to Minecraft's GUI pixel grid. */
public enum Md3Type {
    DISPLAY_LARGE(34, Weight.REGULAR, -0.6f, 42f),
    DISPLAY_MEDIUM(29, Weight.REGULAR, -0.4f, 36f),
    DISPLAY_SMALL(25, Weight.REGULAR, -0.2f, 32f),
    HEADLINE_LARGE(24, Weight.REGULAR, 0f, 32f),
    HEADLINE_MEDIUM(21, Weight.REGULAR, 0f, 28f),
    HEADLINE_SMALL(18, Weight.REGULAR, 0f, 25f),
    TITLE_LARGE(18, Weight.MEDIUM, 0f, 24f),
    TITLE_MEDIUM(15, Weight.MEDIUM, 0f, 21f),
    TITLE_SMALL(13, Weight.MEDIUM, 0f, 19f),
    BODY_LARGE(14, Weight.REGULAR, 0.1f, 20f),
    BODY_MEDIUM(12, Weight.REGULAR, 0.1f, 18f),
    BODY_SMALL(11, Weight.REGULAR, 0.1f, 16f),
    /** 与原版 9px 字体同高的常规字重，用于接管原版文字。 */
    VANILLA_TEXT(9, Weight.REGULAR, 0f, 12f),
    /** 9px 逻辑尺寸，但使用 14px 图集，专供世界中的名牌/告示牌，放大后更清晰。 */
    WORLD_TEXT(9, Weight.REGULAR, 0f, 12f, "regular_14"),
    LABEL_LARGE(13, Weight.MEDIUM, 0.3f, 19f),
    LABEL_MEDIUM(12, Weight.MEDIUM, 0.4f, 17f),
    LABEL_SMALL(10, Weight.MEDIUM, 0.5f, 15f);

    public enum Weight {
        REGULAR("regular"), MEDIUM("medium");

        public final String key;

        Weight(String key) {
            this.key = key;
        }
    }

    public final int size;
    public final Weight weight;
    public final float tracking;
    public final float lineHeight;
    public final String atlasOverride;

    Md3Type(int size, Weight weight, float tracking, float lineHeight) {
        this(size, weight, tracking, lineHeight, null);
    }

    Md3Type(int size, Weight weight, float tracking, float lineHeight, String atlasOverride) {
        this.size = size;
        this.weight = weight;
        this.tracking = tracking;
        this.lineHeight = lineHeight;
        this.atlasOverride = atlasOverride;
    }

    public String atlasKey() {
        return atlasOverride != null ? atlasOverride : weight.key + "_" + size;
    }
}
