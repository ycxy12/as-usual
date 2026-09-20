import { placeService, restaurantService } from "../../services/discover";
import { downloadImage } from "../../services/http";
import type { LeisurePlace, Restaurant } from "../../types";
import {
  confirmDelete,
  dateDisplay,
  money,
  route,
  toast,
} from "../../utils/format";

Page({
  data: {
    type: "restaurant",
    id: "",
    name: "",
    category: "",
    image: "",
    visits: "",
    lastVisit: "",
    fields: [] as { label: string; value: string }[],
    tags: [] as string[],
    action: "今天就吃它",
    busy: false,
    error: "",
  },
  onLoad(options: { type?: string; id?: string }) {
    this.setData({
      type: options.type === "place" ? "place" : "restaurant",
      id: options.id || "",
      action: options.type === "place" ? "这个周末去" : "今天就吃它",
    });
  },
  onShow() {
    void this.load();
  },
  async load() {
    if (!this.data.id) return;
    try {
      const place = this.data.type === "place";
      const value = place
        ? await placeService.get(this.data.id)
        : await restaurantService.get(this.data.id);
      const x = value as LeisurePlace & Restaurant;
      const category = place ? x.category : x.cuisine;
      const fields: [string, string | null][] = place
        ? [
            ["地址", x.address],
            ["距离", x.distanceKm === null ? null : `${x.distanceKm} 公里`],
            ["人均消费", x.averageCost === null ? null : money(x.averageCost)],
            [
              "推荐指数",
              x.recommendation === null ? null : `${x.recommendation} / 5`,
            ],
            ["状态", x.visitStatus],
            ["备注", x.note],
          ]
        : [
            ["地址", x.address],
            ["距离", x.distanceKm === null ? null : `${x.distanceKm} 公里`],
            [
              "人均价格",
              x.averagePrice === null ? null : money(x.averagePrice),
            ],
            ["评分", x.rating === null ? null : `${x.rating} / 5`],
            ["推荐菜", x.recommendedDishes],
            ["避雷菜", x.avoidedDishes],
            ["备注", x.note],
          ];
      this.setData({
        name: x.name,
        category,
        visits: `${place ? "去过" : "吃过"} ${x.visitCount} 次`,
        lastVisit: x.lastVisitedDate
          ? `最近一次 ${dateDisplay(x.lastVisitedDate)}`
          : "还没有去过",
        fields: fields
          .filter((pair): pair is [string, string] => !!pair[1])
          .map(([label, item]) => ({ label, value: item })),
        tags: x.tags,
        image: "",
        error: "",
      });
      wx.setNavigationBarTitle({ title: x.name });
      if (x.imageId)
        void downloadImage(x.imageId)
          .then((image) => this.setData({ image }))
          .catch(() => {});
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  edit() {
    route("pages/discover/form", { type: this.data.type, id: this.data.id });
  },
  async visit() {
    if (this.data.busy) return;
    this.setData({ busy: true });
    try {
      if (this.data.type === "place") await placeService.visit(this.data.id);
      else await restaurantService.visit(this.data.id);
      toast("已记下这次到访");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "记录失败");
    } finally {
      this.setData({ busy: false });
    }
  },
  async remove() {
    if (
      !(await confirmDelete(
        `删除后无法恢复，确定删除这${this.data.type === "place" ? "个地点" : "家餐厅"}吗？`,
      ))
    )
      return;
    try {
      if (this.data.type === "place") await placeService.delete(this.data.id);
      else await restaurantService.delete(this.data.id);
      toast("已删除");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
