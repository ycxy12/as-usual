import { vehicleService } from "../../services/vehicle";
import { categoryNames } from "../../services/categories";
import {
  vehicleCategories,
  type VehicleCategory,
  type VehicleInput,
} from "../../types";
import { alertUnsaved, amount, localDate, toast } from "../../utils/format";
Page({
  data: {
    id: "",
    category: "加油" as VehicleCategory,
    categories: vehicleCategories as readonly string[],
    categoryIndex: 0,
    date: localDate(),
    amount: "",
    odometer: "",
    quantity: "",
    unitPrice: "",
    location: "",
    note: "",
    showEnergy: true,
    showLocation: true,
    saving: false,
  },
  original: "",
  async onLoad(options: { id?: string }) {
    const categories = await categoryNames("vehicle", vehicleCategories);
    this.setData({ categories, category: categories[0], categoryIndex: 0 });
    if (options.id) {
      try {
        const x = await vehicleService.get(options.id);
        this.setData({
          id: x.id,
          category: x.category,
          categoryIndex: categories.indexOf(x.category),
          date: x.occurredAt.slice(0, 10),
          amount: String(x.amount),
          odometer: x.odometer === null ? "" : String(x.odometer),
          quantity: x.quantity === null ? "" : String(x.quantity),
          unitPrice: x.unitPrice === null ? "" : String(x.unitPrice),
          location: x.location || "",
          note: x.note || "",
        });
        this.updateFields();
        wx.setNavigationBarTitle({ title: "编辑用车记录" });
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
      d.amount,
      d.odometer,
      d.quantity,
      d.unitPrice,
      d.location,
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
  categoryChange(e: { detail: { value: string } }) {
    const index = Number(e.detail.value);
    this.setData({
      categoryIndex: index,
      category: this.data.categories[index],
    });
    this.updateFields();
    this.dirty();
  },
  updateFields() {
    const category = this.data.category;
    this.setData({
      showEnergy: category === "加油" || category === "充电",
      showLocation: ["停车", "加油", "充电", "洗车", "维修", "保养"].includes(
        category,
      ),
    });
  },
  async save() {
    if (this.data.saving) return;
    const cost = amount(this.data.amount);
    if (cost === null) {
      toast("请输入有效金额");
      return;
    }
    const odometer = this.data.odometer.trim()
      ? Number(this.data.odometer)
      : null;
    if (odometer !== null && (!Number.isFinite(odometer) || odometer < 0)) {
      toast("里程不能小于 0");
      return;
    }
    const quantity =
      this.data.showEnergy && this.data.quantity.trim()
        ? Number(this.data.quantity)
        : null;
    if (quantity !== null && (!Number.isFinite(quantity) || quantity <= 0)) {
      toast("数量须大于 0");
      return;
    }
    const unitPrice =
      this.data.showEnergy && this.data.unitPrice.trim()
        ? amount(this.data.unitPrice)
        : null;
    if (
      this.data.showEnergy &&
      this.data.unitPrice.trim() &&
      unitPrice === null
    ) {
      toast("请输入有效单价");
      return;
    }
    this.setData({ saving: true });
    try {
      const value: VehicleInput = {
        occurredAt: `${this.data.date}T12:00:00`,
        category: this.data.category,
        amount: cost,
        odometer,
        quantity,
        unitPrice,
        location: this.data.showLocation ? this.data.location.trim() : "",
        note: this.data.note.trim(),
      };
      if (this.data.id) await vehicleService.update(this.data.id, value);
      else await vehicleService.create(value);
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
