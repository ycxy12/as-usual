import { fragmentService } from "../../services/fragment";
import { vehicleService } from "../../services/vehicle";
import { housingService } from "../../services/housing";
import { socialService } from "../../services/social";
import { dateDisplay, localDate, money, route } from "../../utils/format";

interface Recent {
  id: string;
  title: string;
  detail: string;
  path: string;
  occurredAt: string;
}
Page({
  data: {
    today: "",
    todayFragments: [] as { id: string; content: string; time: string }[],
    recent: [] as Recent[],
    vehicleMonth: "¥0.00",
    housingMonth: "¥0.00",
    recentGifts: [] as { id: string; text: string; date: string }[],
    error: "",
  },
  onShow() {
    void this.load();
  },
  async load() {
    this.setData({ today: dateDisplay(localDate()), error: "" });
    try {
      const [fragments, vehicles, housing, vehicleStats, housingStats, gifts] =
        await Promise.all([
          fragmentService.list(),
          vehicleService.list(),
          housingService.list(),
          vehicleService.stats(),
          housingService.stats(),
          socialService.recent(),
        ]);
      const today = localDate();
      const todayFragments = fragments.items
        .filter((x) => x.occurredAt.startsWith(today))
        .slice(0, 3)
        .map((x) => ({
          id: x.id,
          content: x.content,
          time: x.occurredAt.slice(11, 16),
        }));
      const recent: Recent[] = [
        ...fragments.items
          .slice(0, 6)
          .map((x) => ({
            id: x.id,
            title: "每日碎片",
            detail: x.content,
            path: "pages/fragment/detail",
            occurredAt: x.occurredAt,
          })),
        ...vehicles.items
          .slice(0, 6)
          .map((x) => ({
            id: x.id,
            title: `车辆 · ${x.category}`,
            detail: money(x.amount),
            path: "pages/vehicle/form",
            occurredAt: x.occurredAt,
          })),
        ...housing.items
          .slice(0, 6)
          .map((x) => ({
            id: x.id,
            title: `居住 · ${x.category}`,
            detail: money(x.amount),
            path: "pages/housing/form",
            occurredAt: x.occurredAt,
          })),
      ]
        .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
        .slice(0, 6);
      this.setData({
        todayFragments,
        recent,
        vehicleMonth: money(vehicleStats.expenses.month),
        housingMonth: money(housingStats.expenses.month),
        recentGifts: gifts.map((x) => ({
          id: x.id,
          text: `${x.contactName} · ${x.event} · ${x.direction} ${money(x.amount)}`,
          date: dateDisplay(x.occurredAt),
        })),
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败，请下拉重试",
      });
    }
  },
  onPullDownRefresh() {
    void this.load().finally(() => wx.stopPullDownRefresh());
  },
  quickAdd() {
    route("pages/fragment/form");
  },
  fragments() {
    route("pages/fragment/index");
  },
  vehicles() {
    route("pages/vehicle/index");
  },
  housing() {
    route("pages/housing/index");
  },
  social() {
    route("pages/social/index");
  },
  openFragment(e: WechatMiniprogram.BaseEvent) {
    route("pages/fragment/detail", {
      id: e.currentTarget.dataset.id as string,
    });
  },
  openRecent(e: WechatMiniprogram.BaseEvent) {
    route(e.currentTarget.dataset.path as string, {
      id: e.currentTarget.dataset.id as string,
    });
  },
});
