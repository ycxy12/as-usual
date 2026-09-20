import { socialService } from "../../services/social";
import { money, route, toast } from "../../utils/format";
import type { Contact } from "../../types";
interface Row extends Contact {
  sentText: string;
  receivedText: string;
}
let timer: ReturnType<typeof setTimeout> | null = null;
Page({
  data: {
    items: [] as Row[],
    total: 0,
    page: 0,
    q: "",
    loading: false,
    error: "",
  },
  onShow() {
    void this.refresh();
  },
  onUnload() {
    if (timer) clearTimeout(timer);
  },
  row(x: Contact): Row {
    return { ...x, sentText: money(x.sent), receivedText: money(x.received) };
  },
  async refresh() {
    this.setData({ loading: true, error: "" });
    try {
      const result = await socialService.contacts(0, this.data.q);
      this.setData({
        items: result.items.map(this.row),
        total: result.total,
        page: 0,
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  onPullDownRefresh() {
    void this.refresh().finally(() => wx.stopPullDownRefresh());
  },
  onReachBottom() {
    void this.more();
  },
  async more() {
    if (this.data.loading || this.data.items.length >= this.data.total) return;
    this.setData({ loading: true });
    try {
      const page = this.data.page + 1;
      const result = await socialService.contacts(page, this.data.q);
      this.setData({
        items: [...this.data.items, ...result.items.map(this.row)],
        page,
      });
    } catch {
      toast("加载更多失败");
    } finally {
      this.setData({ loading: false });
    }
  },
  search(e: { detail: { value: string } }) {
    this.setData({ q: e.detail.value });
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      void this.refresh();
    }, 350);
  },
  open(e: { currentTarget: { dataset: { id: string } } }) {
    route("pages/social/contact", { id: e.currentTarget.dataset.id });
  },
  add() {
    route("pages/social/contact-form");
  },
});
