import { housingService } from "../../services/housing";
import {
  confirmDelete,
  dateDisplay,
  money,
  route,
  toast,
} from "../../utils/format";
import type { Housing, HousingStats } from "../../types";
interface Row extends Housing {
  dateText: string;
  amountText: string;
}
Page({
  data: {
    items: [] as Row[],
    total: 0,
    page: 0,
    loading: false,
    error: "",
    stats: null as HousingStats | null,
    month: "¥0.00",
    year: "¥0.00",
    average: "¥0.00",
    daily: "¥0.00",
  },
  onShow() {
    void this.refresh();
  },
  async refresh() {
    this.setData({ loading: true, error: "" });
    try {
      const [result, stats] = await Promise.all([
        housingService.list(),
        housingService.stats(),
      ]);
      this.setData({
        items: result.items.map(this.row),
        total: result.total,
        page: 0,
        stats,
        month: money(stats.expenses.month),
        year: money(stats.expenses.year),
        average: money(stats.expenses.monthlyAverage),
        daily: money(stats.dailyAverage),
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  row(x: Housing): Row {
    return {
      ...x,
      dateText: dateDisplay(x.occurredAt),
      amountText: money(x.amount),
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
      const result = await housingService.list(page);
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
  add() {
    route("pages/housing/form");
  },
  edit(e: { currentTarget: { dataset: { id: string } } }) {
    route("pages/housing/form", { id: e.currentTarget.dataset.id });
  },
  async remove(e: { currentTarget: { dataset: { id: string } } }) {
    if (!(await confirmDelete())) return;
    try {
      await housingService.delete(e.currentTarget.dataset.id);
      toast("已删除");
      await this.refresh();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
