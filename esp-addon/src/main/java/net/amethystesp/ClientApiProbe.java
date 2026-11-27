package net.amethystesp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * 诊断用：客户端设置界面一打开，就把 {@code net.client.*} 那一串类的真实字段/方法签名写进日志。
 *
 * <p>存在的意义：本 addon 全靠 mixin 字符串目标和反射摸客户端内部（buildGroups / isOn / toggle /
 * colorOf / setColor / label / Group / Unit / Row），客户端一旦改名或改签名，编译期完全看不出来，
 * 只会在启动时炸 mixin。把真实签名打出来，就能一眼看到该把 @Inject 的 method 改成什么。
 *
 * <p>默认关闭，只有加 {@code -Damethystesp.probe=true} 启动时才会打印，避免正常游戏时刷日志。
 *
 * <p>不主动 Class.forName 去加载客户端类，只在界面已经加载出来之后再反射，避免提前加载干扰 mixin。
 */
public final class ClientApiProbe {

    private static final boolean ENABLED = Boolean.getBoolean("amethystesp.probe");
    private static final Set<String> DUMPED = new HashSet<String>();
    private static final int MAX_MEMBERS = 240;
    private static final String CLIENT_PACKAGE = "net.client";

    private ClientApiProbe() {
    }

    public static void tick(MinecraftClient mc) {
        if (!ENABLED || mc == null) {
            return;
        }
        try {
            Screen screen = mc.currentScreen;
            if (screen == null) {
                return;
            }
            Class<?> type = screen.getClass();
            while (type != null) {
                if (type.getName().startsWith("net.minecraft")) {
                    break;
                }
                dump(type);
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
            // 诊断永远不能影响游戏
        }
    }

    private static void dump(Class<?> type) {
        String name = type.getName();
        if (!name.startsWith(CLIENT_PACKAGE) || !DUMPED.add(name)) {
            return;
        }
        StringBuilder sb = new StringBuilder(2048);
        sb.append("[AmethystESP] API dump: ").append(name).append('\n');
        try {
            int count = 0;
            for (Field f : type.getDeclaredFields()) {
                if (count++ > MAX_MEMBERS) {
                    sb.append("  ... truncated\n");
                    break;
                }
                sb.append("  F ").append(Modifier.toString(f.getModifiers()))
                        .append(' ').append(shortName(f.getType())).append(' ').append(f.getName()).append('\n');
            }
            for (Method m : type.getDeclaredMethods()) {
                if (count++ > MAX_MEMBERS) {
                    sb.append("  ... truncated\n");
                    break;
                }
                sb.append("  M ").append(Modifier.toString(m.getModifiers()))
                        .append(' ').append(shortName(m.getReturnType())).append(' ').append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(shortName(params[i]));
                }
                sb.append(")\n");
            }
        } catch (Throwable t) {
            sb.append("  <dump failed> ").append(t).append('\n');
        }
        AmethystEspAddon.LOGGER.info(sb.toString());

        // 内部类（Group / Unit / Row 这类）也顺手打出来，反射构造它们要用
        try {
            for (Class<?> inner : type.getDeclaredClasses()) {
                dump(inner);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String shortName(Class<?> type) {
        if (type == null) {
            return "?";
        }
        String n = type.getName();
        return n.startsWith("net.client.") || n.startsWith("java.") ? n : type.getSimpleName();
    }
}
