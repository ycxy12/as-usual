import { fragmentService } from "../../services/fragment";
import { deleteImage, downloadImage, uploadImage } from "../../services/http";
import { alertUnsaved, localDate, localTime, toast } from "../../utils/format";

interface ImageRow {
  id: string;
  path: string;
}
Page({
  data: {
    id: "",
    content: "",
    date: localDate(),
    time: localTime(),
    tagsText: "",
    images: [] as ImageRow[],
    saving: false,
    uploading: false,
    dirty: false,
  },
  original: "",
  pendingImageIds: new Set<string>(),
  async onLoad(options: { id?: string }) {
    if (options.id) {
      try {
        const item = await fragmentService.get(options.id);
        const downloaded = await Promise.allSettled(
          item.imageIds.map(downloadImage),
        );
        const images = item.imageIds.map((id, index) => ({
          id,
          path:
            downloaded[index].status === "fulfilled"
              ? downloaded[index].value
              : "",
        }));
        this.setData({
          id: item.id,
          content: item.content,
          date: item.occurredAt.slice(0, 10),
          time: item.occurredAt.slice(11, 16),
          tagsText: item.tags.join(" "),
          images,
        });
        wx.setNavigationBarTitle({ title: "编辑碎片" });
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
  snapshot(): string {
    const d = this.data;
    return JSON.stringify([
      d.content,
      d.date,
      d.time,
      d.tagsText,
      d.images.map((x) => x.id),
    ]);
  },
  checkDirty() {
    const dirty = this.snapshot() !== this.original;
    this.setData({ dirty });
    alertUnsaved(dirty);
  },
  onInput(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
    this.checkDirty();
  },
  onDate(e: { detail: { value: string } }) {
    this.setData({ date: e.detail.value });
    this.checkDirty();
  },
  onTime(e: { detail: { value: string } }) {
    this.setData({ time: e.detail.value });
    this.checkDirty();
  },
  async chooseImages() {
    if (this.data.uploading) return;
    const remaining = 9 - this.data.images.length;
    if (!remaining) return;
    const paths = await new Promise<string[]>((resolve, reject) =>
      wx.chooseImage({
        count: remaining,
        sizeType: ["compressed"],
        success: (r) => resolve(r.tempFilePaths),
        fail: reject,
      }),
    ).catch(() => []);
    if (!paths.length) return;
    this.setData({ uploading: true });
    wx.showLoading({ title: "上传图片" });
    const uploaded: ImageRow[] = [];
    try {
      for (const path of paths) {
        const id = await uploadImage(path);
        this.pendingImageIds.add(id);
        uploaded.push({ id, path });
      }
      this.setData({ images: [...this.data.images, ...uploaded] });
      this.checkDirty();
    } catch (error) {
      uploaded.forEach((image) => this.discardPendingImage(image.id));
      toast(error instanceof Error ? error.message : "图片上传失败");
    } finally {
      this.setData({ uploading: false });
      wx.hideLoading();
    }
  },
  removeImage(e: { currentTarget: { dataset: { index: number } } }) {
    const images = [...this.data.images];
    const [removed] = images.splice(Number(e.currentTarget.dataset.index), 1);
    if (removed) this.discardPendingImage(removed.id);
    this.setData({ images });
    this.checkDirty();
  },
  async save() {
    if (this.data.saving || this.data.uploading) return;
    if (!this.data.content.trim()) {
      toast("写一点内容再保存");
      return;
    }
    if (this.data.content.length > 2000) {
      toast("内容不能超过 2000 字");
      return;
    }
    this.setData({ saving: true });
    try {
      const tags = [
        ...new Set(
          this.data.tagsText
            .split(/[\s,，#]+/)
            .map((x) => x.trim())
            .filter(Boolean),
        ),
      ];
      const value = {
        content: this.data.content.trim(),
        occurredAt: `${this.data.date}T${this.data.time}:00`,
        tags,
        imageIds: this.data.images.map((x) => x.id),
      };
      if (this.data.id) await fragmentService.update(this.data.id, value);
      else await fragmentService.create(value);
      value.imageIds.forEach((id) => this.pendingImageIds.delete(id));
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
