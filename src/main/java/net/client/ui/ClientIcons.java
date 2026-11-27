package net.client.ui;

/** Material icon names for the 26 setting groups (ids 200..225). */
public final class ClientIcons {
   private static final String[] GROUP_ICONS = new String[]{
      "gesture",       // 200 swing
      "healing",       // 201 hurt
      "wb_sunny",      // 202 gamma
      "waves",         // 203 bobbing
      "mouse",         // 214 raw input
      "directions_run",// 204 sprint
      "visibility",    // 205 fov
      "auto_awesome",  // 206 glint
      "cloud",         // 207 fog
      "crop_free",     // 208 block highlight
      "badge",         // 209 name tag
      "chat",          // 210 chat
      "format_list_numbered", // 211 scoreboard
      "thunderstorm",  // 212 weather
      "schedule",      // 213 time
      "blur_on",       // 215 blur
      "animation",     // 221 old animation
      "auto_awesome",  // 222 hit afterimage
      "inventory_2",   // 218 drops
      "tune",          // 220 screen / ui
      "my_location",   // 219 crosshair
      "pan_tool",      // 223 hand
      "360",           // 224 freelook
      "grain",         // 225 particles
   };

   private ClientIcons() {
   }

   public static String group(int groupId) {
      if (groupId == 226) {
         return "crop_free";
      }
      if (groupId >= 200 && groupId <= 225) {
         int index = groupId - 200;
         int cursor = 0;
         int[] ids = {200, 201, 202, 203, 214, 204, 205, 206, 207, 208, 209, 210,
                211, 212, 213, 215, 221, 222, 218, 220, 219, 223, 224, 225};
         for (int i = 0; i < ids.length; i++) {
            if (ids[i] == groupId) {
               cursor = i;
               break;
            }
         }
         if (cursor < GROUP_ICONS.length) {
            return GROUP_ICONS[cursor];
         }
      }
      return "settings";
   }
}
