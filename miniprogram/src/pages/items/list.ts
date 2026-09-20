import { itemService, wardrobeService } from "../../services/items";
import { categoryNames } from "../../services/categories";
import {
  garmentCategories,
  garmentStatuses,
  itemCategories,
  itemStatuses,
  type Garment,
  type PersonalItem,
} from "../../types";
import { downloadImage } from "../../services/http";
import { money, route } from "../../utils/format";

type Row = {
  id: string;
  name: string;
  category: string;
  subtitle: string;
  price: string;
  imageId: string;
  image: string;
  initial: string;
};
Page({
  data: {
    mode: "item",
    title: "个人物品",
    categories: ["全部", ...itemCategories] as string[],
    statuses: ["全部", ...itemStatuses] as string[],
    categoryIndex: 0,
    statusIndex: 0,
    q: "",
    rows: [] as Row[],
    total: 0,
    page: 0,
    loading: false,
    initialized: false,
    error: "",
    stats: "",
    extra: "",
    categoryCounts: [] as { name: string; count: number }[],
    wearRanking: [] as { id: string; name: string; wearCount: number }[],
    purchaseTotal: "",
  },
  loadVersion: 0,
  async onLoad(options: { mode?: string }) {
    const wardrobe = options.mode === "wardrobe";
    const title = wardrobe ? "我的衣柜" : "个人物品";
    this.setData({
      mode: wardrobe ? "wardrobe" : "item",
      title,
      categories: ["全部", ...(wardrobe ? garmentCategories : itemCategories)],
      statuses: ["全部", ...(wardrobe ? garmentStatuses : itemStatuses)],
    });
    wx.setNavigationBarTitle({ title });
    const categories = await categoryNames(
      wardrobe ? "wardrobe" : "item",
      wardrobe ? garmentCategories : itemCategories,
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
        category: this.data.categoryIndex
          ? this.data.categories[this.data.categoryIndex]
          : "",
        status: this.data.statusIndex
          ? this.data.statuses[this.data.statusIndex]
          : "",
      };
      const wardrobe = this.data.mode === "wardrobe";
      const [result, stats] = wardrobe
        ? await Promise.all([
            wardrobeService.list(params),
            wardrobeService.stats(),
          ])
        : await Promise.all([itemService.list(params), itemService.stats()]);
      if (version !== this.loadVersion) return;
      const rows: Row[] = result.items.map((x: Garment | PersonalItem) => ({
        id: x.id,
        name: x.name,
        category: x.category,
        initial: x.category.slice(0, 1),
        subtitle: wardrobe
          ? `${x.status} · 穿着 ${(x as Garment).wearCount} 次`
          : `${x.status} · ${x.brand || "未填写品牌"}`,
        price: wardrobe
          ? (x as Garment).costPerWear === null
            ? ""
            : `${money((x as Garment).costPerWear!)} / 次`
          : (x as PersonalItem).currentValue === null
            ? ""
            : `估值 ${money((x as PersonalItem).currentValue!)}`,
        imageId: x.imageId || "",
        image: "",
      }));
      this.setData({
        rows: reset ? rows : [...this.data.rows, ...rows],
        total: result.total,
        page: page + 1,
        stats: `${stats.total} 件`,
        categoryCounts: Object.entries(stats.categories).map(
          ([name, count]) => ({ name, count }),
        ),
        wearRanking: wardrobe
          ? (stats as Awaited<ReturnType<typeof wardrobeService.stats>>)
              .wearRanking
          : [],
        purchaseTotal: wardrobe
          ? ""
          : money(
              (stats as Awaited<ReturnType<typeof itemService.stats>>)
                .purchaseTotal,
            ),
        extra: wardrobe
          ? `久未穿 ${(stats as Awaited<ReturnType<typeof wardrobeService.stats>>).longUnworn} 件`
          : `估值 ${money((stats as Awaited<ReturnType<typeof itemService.stats>>).currentTotal)}`,
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
  search(e: { detail: { value: string } }) {
    this.setData({ q: e.detail.value });
  },
  applySearch() {
    void this.load(true);
  },
  categoryChange(e: { detail: { value: string } }) {
    this.setData({ categoryIndex: Number(e.detail.value) });
    void this.load(true);
  },
  statusChange(e: { detail: { value: string } }) {
    this.setData({ statusIndex: Number(e.detail.value) });
    void this.load(true);
  },
  onReachBottom() {
    if (this.data.rows.length < this.data.total) void this.load();
  },
  onPullDownRefresh() {
    void this.load(true).finally(() => wx.stopPullDownRefresh());
  },
  add() {
    route("pages/items/form", { mode: this.data.mode });
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route("pages/items/detail", {
      mode: this.data.mode,
      id: e.currentTarget.dataset.id as string,
    });
  },
});
