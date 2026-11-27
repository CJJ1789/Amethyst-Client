package net.client;

/**
 * 仅编译期存在的空壳类：让 mixin 注解处理器能解析目标类与目标方法。
 * 不参与运行，也不会打进 jar（真正的实现在 Amethyst Client 模组里）。
 *
 * <p>对齐 1.0.2_24：开关/颜色的取值已经从 ClientScreen 搬到 SettingsValues，
 * 这里只剩分组构建和行文案两个注入点。
 */
public class ClientScreen {
    private void buildGroups() {
    }

    private String label(int key) {
        return "";
    }
}
