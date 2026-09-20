import { profileService } from "../../services/profile";
import { confirmDelete, toast } from "../../utils/format";

Page({
  data: {
    rows: [] as { label: string; value: string }[],
    confirming: false,
    phrase: "",
    clearing: false,
    error: "",
  },
  onShow() {
    void this.load();
  },
  async load() {
    try {
      const x = await profileService.dataStats();
      this.setData({
        rows: [
          { label: "每日碎片", value: `${x.fragments} 条` },
          { label: "车辆记录", value: `${x.vehicles} 条` },
          { label: "居住记录", value: `${x.housing} 条` },
          { label: "联系人 / 往来", value: `${x.contacts} / ${x.gifts}` },
          { label: "物品 / 衣物", value: `${x.items} / ${x.garments}` },
          { label: "餐厅 / 地点", value: `${x.restaurants} / ${x.places}` },
          { label: "私有图片", value: `${x.images} 张` },
        ],
        error: "",
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  async beginClear() {
    if (
      !(await confirmDelete(
        "这会永久删除当前微信用户的全部日常集数据和图片，且无法恢复。",
      ))
    )
      return;
    this.setData({ confirming: true, phrase: "" });
  },
  input(e: { detail: { value: string } }) {
    this.setData({ phrase: e.detail.value });
  },
  cancel() {
    this.setData({ confirming: false, phrase: "" });
  },
  async clear() {
    if (this.data.phrase.trim() !== "清空") return toast("请输入“清空”");
    if (this.data.clearing) return;
    this.setData({ clearing: true });
    try {
      await profileService.clearData();
      toast("数据已清空");
      this.setData({ confirming: false });
      wx.reLaunch({ url: "/pages/profile/index" });
    } catch (error) {
      toast(error instanceof Error ? error.message : "清空失败");
      this.setData({ clearing: false });
    }
  },
});
