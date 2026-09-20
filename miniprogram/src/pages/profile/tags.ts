import { profileService } from "../../services/profile";
import type { TagView } from "../../types";
import { confirmDelete, toast } from "../../utils/format";

Page({
  data: {
    q: "",
    newName: "",
    tags: [] as TagView[],
    editingOld: "",
    editingName: "",
    loading: false,
    error: "",
  },
  onLoad() {
    void this.load();
  },
  async load() {
    this.setData({ loading: true, error: "", editingOld: "" });
    try {
      this.setData({ tags: await profileService.tags(this.data.q.trim()) });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  search() {
    void this.load();
  },
  async add() {
    const name = this.data.newName.trim();
    if (!name) return toast("请输入标签名称");
    try {
      await profileService.createTag(name);
      this.setData({ newName: "" });
      toast("已添加");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "添加失败");
    }
  },
  startEdit(e: WechatMiniprogram.BaseEvent) {
    const name = e.currentTarget.dataset.name as string;
    this.setData({ editingOld: name, editingName: name });
  },
  cancelEdit() {
    this.setData({ editingOld: "", editingName: "" });
  },
  async saveEdit() {
    const name = this.data.editingName.trim();
    if (!name) return toast("请输入标签名称");
    try {
      await profileService.renameTag(this.data.editingOld, name);
      toast("已更新所有绑定");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "更新失败");
    }
  },
  async remove(e: WechatMiniprogram.BaseEvent) {
    const name = e.currentTarget.dataset.name as string;
    if (
      !(await confirmDelete(`删除 #${name} 后，所有数据上的这个标签都会移除。`))
    )
      return;
    try {
      await profileService.deleteTag(name);
      toast("已删除");
      await this.load();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
