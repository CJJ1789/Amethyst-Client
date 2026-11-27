package net.client.ime;

/**
 * 输入法后端：只负责原生那一侧的组字串、候选窗定位与取消，
 * 平台差异全部收在这里，上层（ImeBridge）只做语义和绘制。
 */
interface ImeBackend {
   boolean available();

   String composition();

   void setCaret(int pixelX, int pixelY);

   void cancel();

   void reset();

   boolean swallowVirtualKey(int vk);
}
