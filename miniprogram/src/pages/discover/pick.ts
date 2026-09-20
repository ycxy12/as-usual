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
import { money, route, toast } from "../../utils/format";

Page({
  data: {
    type: "restaurant",
    title: "今天吃什么",
    categories: ["不限", ...restaurantCuisines] as string[],
    categoryIndex: 0,
    visitLabels: ["不限", "去过", "没去过"],
    visitValues: ["ANY", "VISITED", "NEVER"] as VisitFilter[],
    visitIndex: 0,
    budget: "",
    distance: "",
    excludeRecent7: true,
    loading: false,
    resultId: "",
    resultName: "",
    resultCategory: "",
    resultMeta: "",
    resultImage: "",
    error: "",
    confirmed: false,
    confirming: false,
  },
  async onLoad(options: { type?: string }) {
    const place = options.type === "place";
    const categories = await categoryNames(
      place ? "place" : "restaurant",
      place ? placeCategories : restaurantCuisines,
    );
    this.setData({
      type: place ? "place" : "restaurant",
      title: place ? "周末去哪玩" : "今天吃什么",
      categories: ["不限", ...categories],
    });
    wx.setNavigationBarTitle({ title: place ? "周末去哪玩" : "今天吃什么" });
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  categoryChange(e: { detail: { value: string } }) {
    this.setData({ categoryIndex: Number(e.detail.value) });
  },
  visitChange(e: { detail: { value: string } }) {
    this.setData({ visitIndex: Number(e.detail.value) });
  },
  recentChange(e: { detail: { value: boolean } }) {
    this.setData({ excludeRecent7: e.detail.value });
  },
  number(value: string, label: string): number | null {
    if (!value.trim()) return null;
    if (!/^\d+(\.\d{1,2})?$/.test(value.trim()))
      throw new Error(`${label}无效`);
    return Number(value);
  },
  async choose() {
    if (this.data.loading) return;
    try {
      const budget = this.number(this.data.budget, "预算");
      const distance = this.number(this.data.distance, "距离");
      const params = {
        maxBudget: budget,
        maxDistance: distance,
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
              excludeRecent7: this.data.excludeRecent7,
            }),
      };
      this.setData({ loading: true, error: "", confirmed: false });
      const place = this.data.type === "place";
      const value = place
        ? await placeService.recommend(params)
        : await restaurantService.recommend(params);
      const x = value as LeisurePlace & Restaurant;
      const category = place ? x.category : x.cuisine;
      const cost = place ? x.averageCost : x.averagePrice;
      const meta = [
        x.distanceKm === null ? "" : `${x.distanceKm} 公里`,
        cost === null ? "" : `人均 ${money(cost)}`,
        place && x.recommendation
          ? `推荐 ${x.recommendation}/5`
          : !place && x.rating !== null
            ? `评分 ${x.rating}/5`
            : "",
      ]
        .filter(Boolean)
        .join(" · ");
      this.setData({
        resultId: x.id,
        resultName: x.name,
        resultCategory: category,
        resultMeta: meta,
        resultImage: "",
      });
      if (x.imageId)
        void downloadImage(x.imageId)
          .then((image) => this.setData({ resultImage: image }))
          .catch(() => {});
    } catch (error) {
      this.setData({
        resultId: "",
        error: error instanceof Error ? error.message : "没有找到候选",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  detail() {
    if (this.data.resultId)
      route("pages/discover/detail", {
        type: this.data.type,
        id: this.data.resultId,
      });
  },
  async confirm() {
    if (!this.data.resultId || this.data.confirmed || this.data.confirming)
      return;
    this.setData({ confirming: true });
    try {
      if (this.data.type === "place")
        await placeService.visit(this.data.resultId);
      else await restaurantService.visit(this.data.resultId);
      this.setData({ confirmed: true });
      toast("已记下这次到访");
    } catch (error) {
      toast(error instanceof Error ? error.message : "记录失败");
    } finally {
      this.setData({ confirming: false });
    }
  },
});
