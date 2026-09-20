import { placeService, restaurantService } from "../../services/discover";
import { categoryNames } from "../../services/categories";
import { downloadImage } from "../../services/http";
import {
  placeCategories,
  restaurantCuisines,
  type LeisurePlace,
  type Restaurant,
  type VisitFilter,
} from "../../types";
import { money, route } from "../../utils/format";

type Row = {
  id: string;
  name: string;
  category: string;
  meta: string;
  visits: string;
  imageId: string;
  image: string;
  initial: string;
};
Page({
  data: {
    type: "restaurant",
    title: "我的餐厅",
    categories: ["全部", ...restaurantCuisines] as string[],
    categoryIndex: 0,
    visitLabels: ["全部", "去过", "没去过"],
    visitValues: ["ANY", "VISITED", "NEVER"] as VisitFilter[],
    visitIndex: 0,
    q: "",
    rows: [] as Row[],
    total: 0,
    page: 0,
    loading: false,
    initialized: false,
    error: "",
    summary: "",
  },
  loadVersion: 0,
  async onLoad(options: { type?: string }) {
    const place = options.type === "place";
    const title = place ? "我的地点" : "我的餐厅";
    this.setData({
      type: place ? "place" : "restaurant",
      title,
      categories: ["全部", ...(place ? placeCategories : restaurantCuisines)],
    });
    wx.setNavigationBarTitle({ title });
    const categories = await categoryNames(
      place ? "place" : "restaurant",
      place ? placeCategories : restaurantCuisines,
    );
    this.setData({
      categories: ["全部", ...categories],
      initialized: true,
    });
    void this.load(true);
  },
  onShow() {
    if (this.data.initialized) void this.load(true);
  },
  async load(reset = false) {
    if (!this.data.initialized || (!reset && this.data.loading)) return;
    const version = ++this.loadVersion;
    const page = reset ? 0 : this.data.page;
    this.setData({ loading: true, error: "" });
    try {
      const params = {
        page,
        size: 20,
        q: this.data.q.trim(),
        visited: this.data.visitValues[this.data.visitIndex],
        ...(this.data.type === "place"
          ? {
              category: this.data.categoryIndex
                ? this.data.categories[this.data.categoryIndex]
                : "",
            }
          : {
              cuisine: this.data.categoryIndex
                ? this.data.categories[this.data.categoryIndex]
                : "",
            }),
      };
      const place = this.data.type === "place";
      const [result, stats] = place
        ? await Promise.all([placeService.list(params), placeService.stats()])
        : await Promise.all([
            restaurantService.list(params),
            restaurantService.stats(),
          ]);
      if (version !== this.loadVersion) return;
      const rows: Row[] = result.items.map(
        (value: LeisurePlace | Restaurant) => {
          const x = value as LeisurePlace & Restaurant;
          const category = place ? x.category : x.cuisine;
          const cost = place ? x.averageCost : x.averagePrice;
          return {
            id: x.id,
            name: x.name,
            category,
            initial: category.slice(0, 1),
            meta: [
              x.distanceKm === null ? "" : `${x.distanceKm}km`,
              cost === null ? "" : `人均 ${money(cost)}`,
            ]
              .filter(Boolean)
              .join(" · "),
            visits: x.visitCount
              ? `${place ? "去过" : "吃过"} ${x.visitCount} 次`
              : place
                ? x.visitStatus
                : "还没吃过",
            imageId: x.imageId || "",
            image: "",
          };
        },
      );
      this.setData({
        rows: reset ? rows : [...this.data.rows, ...rows],
        total: result.total,
        page: page + 1,
        summary: place
          ? `${stats.total} 个地点 · ${(stats as Awaited<ReturnType<typeof placeService.stats>>).wishList} 个想去`
          : `${stats.total} 家餐厅 · ${(stats as Awaited<ReturnType<typeof restaurantService.stats>>).visited} 家吃过`,
      });
      result.items.forEach((x) => {
        if (x.imageId)
          void downloadImage(x.imageId)
            .then((image) => {
              const at = this.data.rows.findIndex(
                (row) => row.id === x.id && row.imageId === x.imageId,
              );
              if (at >= 0) this.setData({ [`rows[${at}].image`]: image });
            })
            .catch(() => {});
      });
    } catch (error) {
      if (version === this.loadVersion)
        this.setData({
          error: error instanceof Error ? error.message : "加载失败",
        });
    } finally {
      if (version === this.loadVersion) this.setData({ loading: false });
    }
  },
  input(e: { detail: { value: string } }) {
    this.setData({ q: e.detail.value });
  },
  search() {
    void this.load(true);
  },
  categoryChange(e: { detail: { value: string } }) {
    this.setData({ categoryIndex: Number(e.detail.value) });
    void this.load(true);
  },
  visitChange(e: { detail: { value: string } }) {
    this.setData({ visitIndex: Number(e.detail.value) });
    void this.load(true);
  },
  onReachBottom() {
    if (this.data.rows.length < this.data.total) void this.load();
  },
  onPullDownRefresh() {
    void this.load(true).finally(() => wx.stopPullDownRefresh());
  },
  add() {
    route("pages/discover/form", { type: this.data.type });
  },
  pick() {
    route("pages/discover/pick", { type: this.data.type });
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route("pages/discover/detail", {
      type: this.data.type,
      id: e.currentTarget.dataset.id as string,
    });
  },
});
