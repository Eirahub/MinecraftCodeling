package codeling.robot;

import java.util.List;

/** Stable saved appearance IDs shared by commands, clients and saved entities. */
public final class RobotAppearance {
    public static final List<String> SKINS = List.of("workshop", "industrial", "mint");
    public static final List<String> ANIMATIONS = List.of("idle", "look_left", "look_right", "look_up", "look_down", "wave");

    private RobotAppearance() {
    }

    public static String requireSkin(String skin) {
        if (!SKINS.contains(skin)) throw new IllegalArgumentException("Unknown robot skin: " + skin);
        return skin;
    }

    public static String savedSkin(String skin) {
        return SKINS.contains(skin) ? skin : "workshop";
    }

    public static String requireAnimation(String animation) {
        if (!ANIMATIONS.contains(animation)) throw new IllegalArgumentException("Unknown robot animation: " + animation);
        return animation;
    }

    public static int color(String value) {
        if (value.equals("reset")) return -1;
        if (!value.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use six hex digits or reset");
        return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
    }

    public static int savedColor(int color) {
        return color < -1 || color > 0xFFFFFF ? -1 : color;
    }

    public static float angleDelta(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta >= 180) delta -= 360;
        if (delta < -180) delta += 360;
        return delta;
    }
}
