import { itemService, wardrobeService } from "../../services/items";
import { money, route } from "../../utils/format";

Page({
  data: {
    itemCount: 0,
    itemValue: "¥0.00",
    wardrobeCount: 0,
    longUnworn: 0,
    latest: [] as {
      id: string;
      name: string;
      category: string;
      mode: string;
    }[],
    error: "",
  },
  onShow() {
    void this.load();
  },
  async load() {
    try {
      const [items, clothes, itemStats, wardrobeStats] = await Promise.all([
        itemService.list({ size: 3 }),
        wardrobeService.list({ size: 3 }),
        itemService.stats(),
        wardrobeService.stats(),
      ]);
      this.setData({
        itemCount: itemStats.total,
        itemValue: money(itemStats.currentTotal),
        wardrobeCount: wardrobeStats.total,
        longUnworn: wardrobeStats.longUnworn,
        latest: [
          ...items.items.map((x) => ({
            id: x.id,
            name: x.name,
            category: x.category,
            mode: "item",
          })),
          ...clothes.items.map((x) => ({
            id: x.id,
            name: x.name,
            category: x.category,
            mode: "wardrobe",
          })),
        ].slice(0, 5),
        error: "",
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  onPullDownRefresh() {
    void this.load().finally(() => wx.stopPullDownRefresh());
  },
  items() {
    route("pages/items/list", { mode: "item" });
  },
  wardrobe() {
    route("pages/items/list", { mode: "wardrobe" });
  },
  addItem() {
    route("pages/items/form", { mode: "item" });
  },
  addGarment() {
    route("pages/items/form", { mode: "wardrobe" });
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route("pages/items/detail", {
      mode: e.currentTarget.dataset.mode as string,
      id: e.currentTarget.dataset.id as string,
    });
  },
});
