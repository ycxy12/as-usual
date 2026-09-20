import { placeService, restaurantService } from "../../services/discover";
import { categoryNames } from "../../services/categories";
import { deleteImage, downloadImage, uploadImage } from "../../services/http";
import {
  placeCategories,
  restaurantCuisines,
  type LeisurePlace,
  type LeisurePlaceInput,
  type Restaurant,
  type RestaurantInput,
} from "../../types";
import { alertUnsaved, amount, localDate, toast } from "../../utils/format";

Page({
  data: {
    type: "restaurant",
    id: "",
    title: "添加餐厅",
    name: "",
    categories: [] as string[],
    category: "中餐",
    categoryIndex: 0,
    address: "",
    distanceKm: "",
    cost: "",
    rating: "",
    recommendationLabels: ["未评分", "1", "2", "3", "4", "5"],
    recommendationIndex: 0,
    recommendedDishes: "",
    avoidedDishes: "",
    visitStatuses: ["想去", "去过"],
    visitStatus: "想去",
    visitStatusIndex: 0,
    lastVisitedDate: "",
    visitCount: "0",
    tagsText: "",
    note: "",
    imageId: "",
    imagePath: "",
    today: localDate(),
    uploading: false,
    saving: false,
  },
  original: "",
  pendingImageIds: new Set<string>(),
  async onLoad(options: { type?: string; id?: string }) {
    const place = options.type === "place";
    const categories = await categoryNames(
      place ? "place" : "restaurant",
      place ? placeCategories : restaurantCuisines,
    );
    this.setData({
      type: place ? "place" : "restaurant",
      id: options.id || "",
      title: `${options.id ? "编辑" : "添加"}${place ? "地点" : "餐厅"}`,
      categories,
      category: categories[0],
    });
    wx.setNavigationBarTitle({ title: this.data.title });
    if (options.id) {
      try {
        const x = place
          ? await placeService.get(options.id)
          : await restaurantService.get(options.id);
        this.populate(x);
        if (x.imageId)
          this.setData({ imagePath: await downloadImage(x.imageId) });
      } catch (error) {
        toast(error instanceof Error ? error.message : "加载失败");
        wx.navigateBack();
        return;
      }
    }
    this.original = this.snapshot();
  },
  onUnload() {
    alertUnsaved(false);
    this.pendingImageIds.forEach((id) => this.discardPendingImage(id));
  },
  discardPendingImage(id: string) {
    if (!this.pendingImageIds.has(id)) return;
    void deleteImage(id)
      .then(() => this.pendingImageIds.delete(id))
      .catch(() => {});
  },
  populate(value: LeisurePlace | Restaurant) {
    const x = value as LeisurePlace & Restaurant;
    const place = this.data.type === "place";
    const category = place ? x.category : x.cuisine;
    this.setData({
      name: x.name,
      category,
      categoryIndex: this.data.categories.indexOf(category),
      address: x.address || "",
      distanceKm: x.distanceKm === null ? "" : String(x.distanceKm),
      cost:
        (place ? x.averageCost : x.averagePrice) === null
          ? ""
          : String(place ? x.averageCost : x.averagePrice),
      rating: !place && x.rating !== null ? String(x.rating) : "",
      recommendationIndex: place && x.recommendation ? x.recommendation : 0,
      recommendedDishes: !place ? x.recommendedDishes || "" : "",
      avoidedDishes: !place ? x.avoidedDishes || "" : "",
      visitStatus: place ? x.visitStatus : "想去",
      visitStatusIndex: place && x.visitStatus === "去过" ? 1 : 0,
      lastVisitedDate: x.lastVisitedDate || "",
      visitCount: String(x.visitCount),
      tagsText: x.tags.join(" "),
      note: x.note || "",
      imageId: x.imageId || "",
    });
  },
  snapshot() {
    const d = this.data;
    return JSON.stringify([
      d.name,
      d.category,
      d.address,
      d.distanceKm,
      d.cost,
      d.rating,
      d.recommendationIndex,
      d.recommendedDishes,
      d.avoidedDishes,
      d.visitStatus,
      d.lastVisitedDate,
      d.visitCount,
      d.tagsText,
      d.note,
      d.imageId,
    ]);
  },
  dirty() {
    alertUnsaved(this.snapshot() !== this.original);
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
    this.dirty();
  },
  categoryChange(e: { detail: { value: string } }) {
    const index = Number(e.detail.value);
    this.setData({
      categoryIndex: index,
      category: this.data.categories[index],
    });
    this.dirty();
  },
  recommendationChange(e: { detail: { value: string } }) {
    this.setData({ recommendationIndex: Number(e.detail.value) });
    this.dirty();
  },
  statusChange(e: { detail: { value: string } }) {
    const index = Number(e.detail.value);
    this.setData({
      visitStatusIndex: index,
      visitStatus: this.data.visitStatuses[index],
    });
    this.dirty();
  },
  dateChange(e: { detail: { value: string } }) {
    this.setData({ lastVisitedDate: e.detail.value });
    this.dirty();
  },
  clearDate() {
    this.setData({ lastVisitedDate: "" });
    this.dirty();
  },
  async chooseImage() {
    if (this.data.uploading) return;
    const paths = await new Promise<string[]>((resolve) =>
      wx.chooseImage({
        count: 1,
        sizeType: ["compressed"],
        success: (r) => resolve(r.tempFilePaths),
        fail: () => resolve([]),
      }),
    );
    if (!paths.length) return;
    this.setData({ uploading: true });
    try {
      const previous = this.data.imageId;
      const imageId = await uploadImage(paths[0]);
      this.pendingImageIds.add(imageId);
      this.setData({ imageId, imagePath: paths[0] });
      this.discardPendingImage(previous);
      this.dirty();
    } catch (error) {
      toast(error instanceof Error ? error.message : "上传失败");
    } finally {
      this.setData({ uploading: false });
    }
  },
  removeImage() {
    this.discardPendingImage(this.data.imageId);
    this.setData({ imageId: "", imagePath: "" });
    this.dirty();
  },
  optionalAmount(value: string, label: string): number | null {
    if (!value.trim()) return null;
    const result = amount(value);
    if (result === null) throw new Error(`${label}无效`);
    return result;
  },
  async save() {
    if (this.data.saving) return;
    const d = this.data;
    try {
      if (!d.name.trim()) throw new Error("请填写名称");
      const distanceKm = this.optionalAmount(d.distanceKm, "距离");
      const cost = this.optionalAmount(
        d.cost,
        d.type === "place" ? "人均消费" : "人均价格",
      );
      const count = Number(d.visitCount);
      if (!Number.isSafeInteger(count) || count < 0)
        throw new Error("到访次数无效");
      if (count > 0 && !d.lastVisitedDate)
        throw new Error("请填写最近到访日期");
      const tags = d.tagsText.trim()
        ? d.tagsText
            .trim()
            .split(/[\s,，]+/)
            .filter(Boolean)
        : [];
      if (tags.length > 20) throw new Error("最多 20 个标签");
      const common = {
        name: d.name.trim(),
        address: d.address.trim() || null,
        distanceKm,
        lastVisitedDate: d.lastVisitedDate || null,
        visitCount: count,
        note: d.note.trim() || null,
        tags,
        imageId: d.imageId || null,
      };
      this.setData({ saving: true });
      if (d.type === "place") {
        if (d.visitStatus === "去过" && count === 0)
          throw new Error("去过的地点至少填写一次到访");
        const value: LeisurePlaceInput = {
          ...common,
          category: d.category,
          averageCost: cost,
          recommendation: d.recommendationIndex || null,
          visitStatus: d.visitStatus as "想去" | "去过",
        };
        if (d.id) await placeService.update(d.id, value);
        else await placeService.create(value);
      } else {
        const rating = d.rating.trim() ? Number(d.rating) : null;
        if (
          rating !== null &&
          (!Number.isFinite(rating) ||
            rating < 0 ||
            rating > 5 ||
            !/^\d(\.\d)?$/.test(d.rating.trim()))
        )
          throw new Error("评分须为 0 到 5，最多一位小数");
        const value: RestaurantInput = {
          ...common,
          cuisine: d.category,
          averagePrice: cost,
          rating,
          recommendedDishes: d.recommendedDishes.trim() || null,
          avoidedDishes: d.avoidedDishes.trim() || null,
        };
        if (d.id) await restaurantService.update(d.id, value);
        else await restaurantService.create(value);
      }
      this.pendingImageIds.delete(d.imageId);
      this.pendingImageIds.forEach((id) => this.discardPendingImage(id));
      alertUnsaved(false);
      this.original = this.snapshot();
      toast("已保存");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "保存失败");
    } finally {
      this.setData({ saving: false });
    }
  },
});
