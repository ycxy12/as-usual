import { socialService } from "../../services/social";
import {
  confirmDelete,
  dateDisplay,
  money,
  route,
  toast,
} from "../../utils/format";
import type { Contact, Gift } from "../../types";
interface GiftRow extends Gift {
  dateText: string;
  amountText: string;
}
Page({
  data: {
    id: "",
    contact: null as Contact | null,
    gifts: [] as GiftRow[],
    total: 0,
    page: 0,
    loading: false,
    error: "",
    sent: "¥0.00",
    received: "¥0.00",
    difference: "¥0.00",
  },
  onLoad(options: { id?: string }) {
    this.setData({ id: options.id || "" });
  },
  onShow() {
    void this.refresh();
  },
  row(x: Gift): GiftRow {
    return {
      ...x,
      dateText: dateDisplay(x.occurredAt),
      amountText: money(x.amount),
    };
  },
  async refresh() {
    if (!this.data.id) return;
    this.setData({ loading: true, error: "" });
    try {
      const [contact, result] = await Promise.all([
        socialService.contact(this.data.id),
        socialService.gifts(this.data.id),
      ]);
      this.setData({
        contact,
        gifts: result.items.map(this.row),
        total: result.total,
        page: 0,
        sent: money(contact.sent),
        received: money(contact.received),
        difference: money(contact.received - contact.sent),
      });
      wx.setNavigationBarTitle({ title: contact.name });
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
    if (this.data.loading || this.data.gifts.length >= this.data.total) return;
    this.setData({ loading: true });
    try {
      const page = this.data.page + 1;
      const result = await socialService.gifts(this.data.id, page);
      this.setData({
        gifts: [...this.data.gifts, ...result.items.map(this.row)],
        page,
      });
    } catch {
      toast("加载更多失败");
    } finally {
      this.setData({ loading: false });
    }
  },
  add() {
    route("pages/social/gift-form", { contactId: this.data.id });
  },
  edit() {
    route("pages/social/contact-form", { id: this.data.id });
  },
  editGift(e: { currentTarget: { dataset: { id: string } } }) {
    route("pages/social/gift-form", {
      contactId: this.data.id,
      id: e.currentTarget.dataset.id,
    });
  },
  async removeGift(e: { currentTarget: { dataset: { id: string } } }) {
    if (!(await confirmDelete())) return;
    try {
      await socialService.deleteGift(this.data.id, e.currentTarget.dataset.id);
      toast("已删除");
      await this.refresh();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
  async removeContact() {
    if (
      !(await confirmDelete(
        "删除联系人后，名下所有往来记录也会删除，确定继续吗？",
      ))
    )
      return;
    try {
      await socialService.deleteContact(this.data.id);
      toast("已删除");
      wx.navigateBack();
    } catch (error) {
      toast(error instanceof Error ? error.message : "删除失败");
    }
  },
});
