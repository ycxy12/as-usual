import { profileService } from "../../services/profile";
import type { CategoryModule, UserCategory } from "../../types";
import { confirmDelete, toast } from "../../utils/format";

const modules: { label: string; value: CategoryModule }[] = [
  { label: "个人物品", value: "item" },
  { label: "衣柜", value: "wardrobe" },
  { label: "车辆费用", value: "vehicle" },
  { label: "居住费用", value: "housing" },
  { label: "餐厅菜系", value: "restaurant" },
  { label: "地点类型", value: "place" },
];
Page({
  data: {
    moduleLabels: modules.map((x) => x.label),
    moduleIndex: 0,
    categories: [] as UserCategory[],
    newName: "",
    editingId: "",
    editingName: "",
    loading: false,
    error: "",
  },
  onLoad() {
    void this.load();
  },
  module(): CategoryModule {
    return modules[this.data.moduleIndex].value;
  },
  async load() {
    this.setData({ loading: true, error: "", editingId: "" });
    try {
      this.setData({
        categories: await profileService.categories(this.module()),
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  moduleChange(e: { detail: { value: string } }) {
    this.setData({ moduleIndex: Number(e.detail.value), newName: "" });
    void this.load();
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  async add() {
    const name = this.data.newName.trim();
    if (!name) return toast("请输入分类名称");
    try {
      await profileService.createCategory(this.module(), name);
      this.setData({ newName: "" });
      toast("已添加");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "添加失败");
    }
  },
  startEdit(e: WechatMiniprogram.BaseEvent) {
    const item = this.data.categories.find(
      (x) => x.id === e.currentTarget.dataset.id,
    );
    if (item) this.setData({ editingId: item.id, editingName: item.name });
  },
  cancelEdit() {
    this.setData({ editingId: "", editingName: "" });
  },
  async saveEdit() {
    const name = this.data.editingName.trim();
    if (!name) return toast("请输入分类名称");
    try {
      await profileService.renameCategory(this.data.editingId, name);
      toast("已更新，已有数据同步完成");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "更新失败");
    }
  },
  async remove(e: WechatMiniprogram.BaseEvent) {
    const id = e.currentTarget.dataset.id as string;
    if (!(await confirmDelete("只有未被数据使用的分类可以删除。确定继续吗？")))
      return;
    try {
      await profileService.deleteCategory(id);
      toast("已删除");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
