import { money } from "../../utils/format";
Component({
  properties: {
    categories: { type: Array, value: [] },
    months: { type: Array, value: [] },
    total: { type: Number, value: 0 },
  },
  data: {
    categoryRows: [] as {
      label: string;
      amountText: string;
      percent: number;
    }[],
    monthRows: [] as { label: string; amountText: string }[],
  },
  observers: {
    "categories,months,total": function (
      categories: { label: string; amount: number }[],
      months: { label: string; amount: number }[],
      total: number,
    ) {
      this.setData({
        categoryRows: (categories || []).map((x) => ({
          label: x.label,
          amountText: money(x.amount),
          percent: total ? Math.round((x.amount / total) * 100) : 0,
        })),
        monthRows: (months || [])
          .slice(-6)
          .map((x) => ({ label: x.label, amountText: money(x.amount) })),
      });
    },
  },
});
