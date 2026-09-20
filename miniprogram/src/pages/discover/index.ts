import { placeService, restaurantService } from "../../services/discover";
import { route } from "../../utils/format";

Page({
  data: {
    restaurantTotal: 0,
    restaurantVisited: 0,
    placeTotal: 0,
    placeWish: 0,
    recent: [] as { id: string; name: string; type: string; detail: string }[],
    error: "",
  },
  onShow() {
    void this.load();
  },
  async load() {
    try {
      const [restaurants, places, restaurantStats, placeStats] =
        await Promise.all([
          restaurantService.list({ size: 3, visited: "VISITED" }),
          placeService.list({ size: 3, visited: "VISITED" }),
          restaurantService.stats(),
          placeService.stats(),
        ]);
      const recent = [
        ...restaurants.items.map((x) => ({
          id: x.id,
          name: x.name,
          type: "restaurant",
          detail: `${x.cuisine} · 吃过 ${x.visitCount} 次`,
        })),
        ...places.items.map((x) => ({
          id: x.id,
          name: x.name,
          type: "place",
          detail: `${x.category} · 去过 ${x.visitCount} 次`,
        })),
      ].slice(0, 5);
      this.setData({
        restaurantTotal: restaurantStats.total,
        restaurantVisited: restaurantStats.visited,
        placeTotal: placeStats.total,
        placeWish: placeStats.wishList,
        recent,
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
  list(e: WechatMiniprogram.BaseEvent) {
    route("pages/discover/list", {
      type: e.currentTarget.dataset.type as string,
    });
  },
  pick(e: WechatMiniprogram.BaseEvent) {
    route("pages/discover/pick", {
      type: e.currentTarget.dataset.type as string,
    });
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route("pages/discover/detail", {
      type: e.currentTarget.dataset.type as string,
      id: e.currentTarget.dataset.id as string,
    });
  },
});
