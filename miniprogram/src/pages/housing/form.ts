import { housingService } from "../../services/housing";
import { categoryNames } from "../../services/categories";
import {
  housingCategories,
  type HousingCategory,
  type HousingInput,
} from "../../types";
import { alertUnsaved, amount, localDate, toast } from "../../utils/format";
Page({
  data: {
    id: "",
    category: "房租 / 房贷" as HousingCategory,
    categories: housingCategories as readonly string[],
    categoryIndex: 0,
    date: localDate(),
    billingMonth: localDate().slice(0, 7),
    amount: "",
    note: "",
    saving: false,
  },
  original: "",
  async onLoad(options: { id?: string }) {
    const categories = await categoryNames("housing", housingCategories);
    this.setData({ categories, category: categories[0], categoryIndex: 0 });
    if (options.id) {
      try {
        const x = await housingService.get(options.id);
        this.setData({
          id: x.id,
          category: x.category,
          categoryIndex: categories.indexOf(x.category),
          date: x.occurredAt.slice(0, 10),
          billingMonth: x.billingMonth,
          amount: String(x.amount),
          note: x.note || "",
        });
        wx.setNavigationBarTitle({ title: "编辑居住费用" });
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
  },
  snapshot(): string {
    const d = this.data;
    return JSON.stringify([
      d.category,
      d.date,
      d.billingMonth,
      d.amount,
      d.note,
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
  dateChange(e: { detail: { value: string } }) {
    this.setData({ date: e.detail.value });
    this.dirty();
  },
  monthChange(e: { detail: { value: string } }) {
    this.setData({ billingMonth: e.detail.value });
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
  async save() {
    if (this.data.saving) return;
    const cost = amount(this.data.amount);
    if (cost === null) {
      toast("请输入有效金额");
      return;
    }
    this.setData({ saving: true });
    try {
      const value: HousingInput = {
        occurredAt: `${this.data.date}T12:00:00`,
        category: this.data.category,
        amount: cost,
        billingMonth: this.data.billingMonth,
        note: this.data.note.trim(),
      };
      if (this.data.id) await housingService.update(this.data.id, value);
      else await housingService.create(value);
      alertUnsaved(false);
      this.original = this.snapshot();
      toast(this.data.id ? "已更新" : "已记录");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "保存失败");
    } finally {
      this.setData({ saving: false });
    }
  },
});
