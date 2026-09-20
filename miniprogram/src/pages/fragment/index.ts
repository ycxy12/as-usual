import { fragmentService } from "../../services/fragment";
import { dateDisplay, route, toast } from "../../utils/format";
import type { Fragment } from "../../types";

interface Row extends Fragment {
  dateText: string;
  timeText: string;
  preview: string;
}
let searchTimer: ReturnType<typeof setTimeout> | null = null;
Page({
  data: {
    items: [] as Row[],
    total: 0,
    page: 0,
    query: "",
    tag: "",
    date: "",
    tags: [] as string[],
    loading: false,
    error: "",
  },
  onShow() {
    void this.refresh();
  },
  onUnload() {
    if (searchTimer) clearTimeout(searchTimer);
  },
  async refresh() {
    this.setData({ loading: true, error: "" });
    try {
      const [result, tags] = await Promise.all([
        fragmentService.list(0, this.data.query, this.data.tag, this.data.date),
        fragmentService.tags(),
      ]);
      this.setData({
        items: result.items.map(this.row),
        total: result.total,
        page: 0,
        tags,
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  row(x: Fragment): Row {
    return {
      ...x,
      dateText: dateDisplay(x.occurredAt),
      timeText: x.occurredAt.slice(11, 16),
      preview: x.content.length > 90 ? x.content.slice(0, 90) + "…" : x.content,
    };
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
      const result = await fragmentService.list(
        page,
        this.data.query,
        this.data.tag,
        this.data.date,
      );
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
  onSearch(e: WechatMiniprogram.Input) {
    this.setData({ query: e.detail.value });
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      void this.refresh();
    }, 350);
  },
  onTag(e: WechatMiniprogram.BaseEvent) {
    const tag = e.currentTarget.dataset.tag as string;
    this.setData({ tag: this.data.tag === tag ? "" : tag });
    void this.refresh();
  },
  onDate(e: WechatMiniprogram.PickerChange) {
    this.setData({ date: e.detail.value as string });
    void this.refresh();
  },
  clearDate() {
    this.setData({ date: "" });
    void this.refresh();
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route("pages/fragment/detail", {
      id: e.currentTarget.dataset.id as string,
    });
  },
  add() {
    route("pages/fragment/form");
  },
});
