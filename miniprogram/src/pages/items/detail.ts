import { itemService, wardrobeService } from "../../services/items";
import { downloadImage } from "../../services/http";
import type { Garment, PersonalItem } from "../../types";
import {
  confirmDelete,
  dateDisplay,
  money,
  route,
  toast,
} from "../../utils/format";

Page({
  data: {
    id: "",
    mode: "item",
    title: "物品详情",
    image: "",
    name: "",
    category: "",
    status: "",
    tags: [] as string[],
    fields: [] as { label: string; value: string }[],
    stats: [] as { label: string; value: string }[],
    canWear: false,
    busy: false,
    error: "",
  },
  onLoad(options: { mode?: string; id?: string }) {
    this.setData({
      mode: options.mode === "wardrobe" ? "wardrobe" : "item",
      id: options.id || "",
    });
  },
  onShow() {
    void this.load();
  },
  async load() {
    if (!this.data.id) return;
    try {
      const wardrobe = this.data.mode === "wardrobe";
      const x = wardrobe
        ? await wardrobeService.get(this.data.id)
        : await itemService.get(this.data.id);
      const fields = wardrobe
        ? this.garmentFields(x as Garment)
        : this.itemFields(x as PersonalItem);
      const stats = wardrobe
        ? this.garmentStats(x as Garment)
        : this.itemStats(x as PersonalItem);
      this.setData({
        title: wardrobe ? "衣柜详情" : "物品详情",
        name: x.name,
        category: x.category,
        status: x.status,
        tags: x.tags,
        fields,
        stats,
        canWear: wardrobe,
        image: "",
        error: "",
      });
      wx.setNavigationBarTitle({ title: x.name });
      if (x.imageId) {
        void downloadImage(x.imageId)
          .then((image) => this.setData({ image }))
          .catch(() => {});
      }
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  itemFields(x: PersonalItem) {
    return [
      ["品牌", x.brand],
      ["型号", x.model],
      ["购买日期", x.purchaseDate && dateDisplay(x.purchaseDate)],
      ["购买价格", x.purchasePrice === null ? null : money(x.purchasePrice)],
      ["当前估值", x.currentValue === null ? null : money(x.currentValue)],
      ["保修截止", x.warrantyUntil && dateDisplay(x.warrantyUntil)],
      [
        "保修状态",
        x.warrantyActive === null
          ? null
          : x.warrantyActive
            ? "保修中"
            : "已过保",
      ],
      ["存放位置", x.storageLocation],
      ["备注", x.note],
    ]
      .filter((pair): pair is string[] => !!pair[1])
      .map((pair) => ({ label: pair[0], value: pair[1] }));
  },
  itemStats(x: PersonalItem) {
    return [
      {
        label: "已持有",
        value: x.heldDays === null ? "—" : `${x.heldDays} 天`,
      },
      {
        label: "折旧金额",
        value: x.depreciation === null ? "—" : money(x.depreciation),
      },
      {
        label: "使用成本",
        value: x.dailyCost === null ? "—" : `${money(x.dailyCost)} / 天`,
      },
    ];
  },
  garmentFields(x: Garment) {
    return [
      ["品牌", x.brand],
      ["颜色", x.color],
      ["尺码", x.size],
      ["季节", x.season],
      ["购买日期", x.purchaseDate && dateDisplay(x.purchaseDate)],
      ["购买价格", x.purchasePrice === null ? null : money(x.purchasePrice)],
      ["最近穿着", x.lastWornDate && dateDisplay(x.lastWornDate)],
    ]
      .filter((pair): pair is string[] => !!pair[1])
      .map((pair) => ({ label: pair[0], value: pair[1] }));
  },
  garmentStats(x: Garment) {
    return [
      { label: "穿着次数", value: `${x.wearCount} 次` },
      {
        label: "单次成本",
        value: x.costPerWear === null ? "—" : money(x.costPerWear),
      },
      { label: "穿着状态", value: x.longUnworn ? "90 天未穿" : "近期穿着" },
    ];
  },
  edit() {
    route("pages/items/form", { mode: this.data.mode, id: this.data.id });
  },
  async wear() {
    if (this.data.busy) return;
    this.setData({ busy: true });
    try {
      await wardrobeService.wear(this.data.id);
      toast("今天穿过了");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "记录失败");
    } finally {
      this.setData({ busy: false });
    }
  },
  async remove() {
    if (!(await confirmDelete("删除后无法恢复，确定删除这件物品吗？"))) return;
    try {
      if (this.data.mode === "wardrobe")
        await wardrobeService.delete(this.data.id);
      else await itemService.delete(this.data.id);
      toast("已删除");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
