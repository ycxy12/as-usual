import { fragmentService } from "../../services/fragment";
import { downloadImage } from "../../services/http";
import { confirmDelete, dateDisplay, route, toast } from "../../utils/format";
import type { Fragment } from "../../types";
Page({
  data: {
    id: "",
    item: null as Fragment | null,
    dateText: "",
    timeText: "",
    imagePaths: [] as string[],
    error: "",
  },
  onLoad(options: { id?: string }) {
    this.setData({ id: options.id || "" });
  },
  onShow() {
    void this.load();
  },
  async load() {
    if (!this.data.id) return;
    try {
      const item = await fragmentService.get(this.data.id);
      const images = await Promise.allSettled(item.imageIds.map(downloadImage));
      this.setData({
        item,
        dateText: dateDisplay(item.occurredAt),
        timeText: item.occurredAt.slice(11, 16),
        imagePaths: images
          .filter(
            (r): r is PromiseFulfilledResult<string> =>
              r.status === "fulfilled",
          )
          .map((r) => r.value),
        error: "",
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  edit() {
    route("pages/fragment/form", { id: this.data.id });
  },
  back() {
    wx.navigateBack();
  },
  preview(e: { currentTarget: { dataset: { path: string } } }) {
    wx.previewImage({
      current: e.currentTarget.dataset.path,
      urls: this.data.imagePaths,
    });
  },
  async remove() {
    if (!(await confirmDelete())) return;
    try {
      await fragmentService.delete(this.data.id);
      toast("已删除");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
