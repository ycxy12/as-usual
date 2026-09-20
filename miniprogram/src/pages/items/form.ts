import { itemService, wardrobeService } from "../../services/items";
import { categoryNames } from "../../services/categories";
import { deleteImage, downloadImage, uploadImage } from "../../services/http";
import {
  garmentCategories,
  garmentSeasons,
  garmentStatuses,
  itemCategories,
  itemStatuses,
  type Garment,
  type PersonalItem,
  type PersonalItemInput,
  type GarmentInput,
} from "../../types";
import { alertUnsaved, amount, localDate, toast } from "../../utils/format";

Page({
  data: {
    mode: "item",
    id: "",
    title: "添加物品",
    name: "",
    category: "数码产品",
    categories: [] as string[],
    categoryIndex: 0,
    status: "在用",
    statuses: [] as string[],
    statusIndex: 0,
    brand: "",
    model: "",
    purchaseDate: "",
    purchasePrice: "",
    currentValue: "",
    warrantyUntil: "",
    storageLocation: "",
    note: "",
    color: "",
    size: "",
    season: "",
    seasons: garmentSeasons as readonly string[],
    seasonIndex: 0,
    wearCount: "0",
    lastWornDate: "",
    tagsText: "",
    imageId: "",
    imagePath: "",
    uploading: false,
    saving: false,
    today: localDate(),
  },
  original: "",
  pendingImageIds: new Set<string>(),
  async onLoad(options: { mode?: string; id?: string }) {
    const wardrobe = options.mode === "wardrobe";
    const mode = wardrobe ? "wardrobe" : "item";
    const categories = await categoryNames(
      wardrobe ? "wardrobe" : "item",
      wardrobe ? garmentCategories : itemCategories,
    );
    this.setData({
      mode,
      id: options.id || "",
      title: `${options.id ? "编辑" : "添加"}${wardrobe ? "衣物" : "物品"}`,
      categories,
      statuses: [...(wardrobe ? garmentStatuses : itemStatuses)],
      category: categories[0],
      status: wardrobe ? "在穿" : "在用",
    });
    wx.setNavigationBarTitle({ title: this.data.title });
    if (options.id) {
      try {
        const x = wardrobe
          ? await wardrobeService.get(options.id)
          : await itemService.get(options.id);
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
  populate(x: PersonalItem | Garment) {
    const wardrobe = this.data.mode === "wardrobe";
    const common = {
      name: x.name,
      category: x.category,
      categoryIndex: this.data.categories.indexOf(x.category),
      status: x.status,
      statusIndex: this.data.statuses.indexOf(x.status),
      brand: x.brand || "",
      purchaseDate: x.purchaseDate || "",
      purchasePrice: x.purchasePrice === null ? "" : String(x.purchasePrice),
      tagsText: x.tags.join(" "),
      imageId: x.imageId || "",
    };
    this.setData(
      wardrobe
        ? {
            ...common,
            color: (x as Garment).color || "",
            size: (x as Garment).size || "",
            season: (x as Garment).season || "",
            seasonIndex: garmentSeasons.indexOf(
              ((x as Garment).season || "") as (typeof garmentSeasons)[number],
            ),
            wearCount: String((x as Garment).wearCount),
            lastWornDate: (x as Garment).lastWornDate || "",
          }
        : {
            ...common,
            model: (x as PersonalItem).model || "",
            currentValue:
              (x as PersonalItem).currentValue === null
                ? ""
                : String((x as PersonalItem).currentValue),
            warrantyUntil: (x as PersonalItem).warrantyUntil || "",
            storageLocation: (x as PersonalItem).storageLocation || "",
            note: (x as PersonalItem).note || "",
          },
    );
  },
  snapshot(): string {
    const d = this.data;
    return JSON.stringify([
      d.name,
      d.category,
      d.status,
      d.brand,
      d.model,
      d.purchaseDate,
      d.purchasePrice,
      d.currentValue,
      d.warrantyUntil,
      d.storageLocation,
      d.note,
      d.color,
      d.size,
      d.season,
      d.wearCount,
      d.lastWornDate,
      d.tagsText,
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
  pick(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    const field = e.currentTarget.dataset.field;
    const index = Number(e.detail.value);
    if (field === "category")
      this.setData({
        categoryIndex: index,
        category: this.data.categories[index],
      });
    if (field === "status")
      this.setData({ statusIndex: index, status: this.data.statuses[index] });
    if (field === "season")
      this.setData({ seasonIndex: index, season: garmentSeasons[index] });
    this.dirty();
  },
  datePick(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
    this.dirty();
  },
  clearDate(e: { currentTarget: { dataset: { field: string } } }) {
    this.setData({ [e.currentTarget.dataset.field]: "" });
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
  async save() {
    if (this.data.saving) return;
    const d = this.data;
    if (!d.name.trim()) return toast("请填写名称");
    const price = d.purchasePrice.trim() ? amount(d.purchasePrice) : null;
    if (d.purchasePrice.trim() && price === null) return toast("购买价格无效");
    const tags = d.tagsText.trim()
      ? d.tagsText
          .trim()
          .split(/[\s,，]+/)
          .filter(Boolean)
      : [];
    if (tags.length > 20) return toast("最多 20 个标签");
    const common = {
      name: d.name.trim(),
      category: d.category,
      brand: d.brand.trim() || null,
      purchaseDate: d.purchaseDate || null,
      purchasePrice: price,
      status: d.status,
      tags,
      imageId: d.imageId || null,
    };
    this.setData({ saving: true });
    try {
      if (d.mode === "wardrobe") {
        const count = Number(d.wearCount);
        if (!Number.isSafeInteger(count) || count < 0)
          throw new Error("穿着次数无效");
        if (count > 0 && !d.lastWornDate) throw new Error("请填写最近穿着日期");
        const value: GarmentInput = {
          ...common,
          color: d.color.trim() || null,
          size: d.size.trim() || null,
          season: d.season || null,
          wearCount: count,
          lastWornDate: d.lastWornDate || null,
        };
        if (d.id) await wardrobeService.update(d.id, value);
        else await wardrobeService.create(value);
      } else {
        const current = d.currentValue.trim() ? amount(d.currentValue) : null;
        if (d.currentValue.trim() && current === null)
          throw new Error("当前估值无效");
        const value: PersonalItemInput = {
          ...common,
          model: d.model.trim() || null,
          currentValue: current,
          warrantyUntil: d.warrantyUntil || null,
          storageLocation: d.storageLocation.trim() || null,
          note: d.note.trim() || null,
        };
        if (d.id) await itemService.update(d.id, value);
        else await itemService.create(value);
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
