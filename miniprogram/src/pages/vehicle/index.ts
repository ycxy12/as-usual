import { vehicleService } from "../../services/vehicle";
import {
  confirmDelete,
  dateDisplay,
  money,
  route,
  toast,
} from "../../utils/format";
import type { Vehicle, VehicleStats } from "../../types";
interface Row extends Vehicle {
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
    stats: null as VehicleStats | null,
    month: "¥0.00",
    year: "¥0.00",
    cumulative: "¥0.00",
    average: "¥0.00",
    perKm: "—",
    distance: "",
    mileage: [] as { date: string; value: string }[],
  },
  onShow() {
    void this.refresh();
  },
  async refresh() {
    this.setData({ loading: true, error: "" });
    try {
      const [result, stats] = await Promise.all([
        vehicleService.list(),
        vehicleService.stats(),
      ]);
      this.setData({
        items: result.items.map(this.row),
        total: result.total,
        page: 0,
        stats,
        month: money(stats.expenses.month),
        year: money(stats.expenses.year),
        cumulative: money(stats.expenses.total),
        average: money(stats.expenses.monthlyAverage),
        perKm: stats.distance ? money(stats.costPerKm) : "—",
        distance: stats.distance
          ? `已记录 ${stats.distance} 公里`
          : "需两次里程记录",
        mileage: stats.mileage
          .slice(-6)
          .reverse()
          .map((x) => ({
            date: dateDisplay(x.occurredAt),
            value: `${x.odometer} km`,
          })),
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    } finally {
      this.setData({ loading: false });
    }
  },
  row(x: Vehicle): Row {
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
      const result = await vehicleService.list(page);
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
    route("pages/vehicle/form");
  },
  edit(e: { currentTarget: { dataset: { id: string } } }) {
    route("pages/vehicle/form", { id: e.currentTarget.dataset.id });
  },
  async remove(e: { currentTarget: { dataset: { id: string } } }) {
    if (!(await confirmDelete())) return;
    try {
      await vehicleService.delete(e.currentTarget.dataset.id);
      toast("已删除");
      await this.refresh();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
