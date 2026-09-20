import { socialService } from "../../services/social";
import { alertUnsaved, toast } from "../../utils/format";
Page({
  data: { id: "", name: "", relation: "", note: "", saving: false },
  original: "",
  async onLoad(options: { id?: string }) {
    if (options.id) {
      try {
        const x = await socialService.contact(options.id);
        this.setData({
          id: x.id,
          name: x.name,
          relation: x.relation || "",
          note: x.note || "",
        });
        wx.setNavigationBarTitle({ title: "编辑联系人" });
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
    return JSON.stringify([d.name, d.relation, d.note]);
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
    alertUnsaved(this.snapshot() !== this.original);
  },
  async save() {
    if (this.data.saving) return;
    if (!this.data.name.trim()) {
      toast("请输入联系人姓名");
      return;
    }
    this.setData({ saving: true });
    try {
      const value = {
        name: this.data.name.trim(),
        relation: this.data.relation.trim(),
        note: this.data.note.trim(),
      };
      if (this.data.id) await socialService.updateContact(this.data.id, value);
      else await socialService.createContact(value);
      alertUnsaved(false);
      this.original = this.snapshot();
      toast(this.data.id ? "已更新" : "已添加");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "保存失败");
    } finally {
      this.setData({ saving: false });
    }
  },
});
