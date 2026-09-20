import { socialService } from "../../services/social";
import type { GiftDirection, GiftInput, GiftKind } from "../../types";
import { alertUnsaved, amount, localDate, toast } from "../../utils/format";
Page({
  data: {
    id: "",
    contactId: "",
    contactName: "",
    date: localDate(),
    event: "",
    direction: "送出" as GiftDirection,
    kind: "现金" as GiftKind,
    amount: "",
    giftName: "",
    note: "",
    saving: false,
  },
  original: "",
  async onLoad(options: { contactId?: string; id?: string }) {
    const contactId = options.contactId || "";
    this.setData({ contactId });
    try {
      const contact = await socialService.contact(contactId);
      this.setData({ contactName: contact.name });
      if (options.id) {
        const x = await socialService.gift(contactId, options.id);
        this.setData({
          id: x.id,
          date: x.occurredAt.slice(0, 10),
          event: x.event,
          direction: x.direction,
          kind: x.kind,
          amount: String(x.amount),
          giftName: x.giftName || "",
          note: x.note || "",
        });
        wx.setNavigationBarTitle({ title: "编辑往来" });
      }
    } catch (error) {
      toast(error instanceof Error ? error.message : "加载失败");
      wx.navigateBack();
      return;
    }
    this.original = this.snapshot();
  },
  onUnload() {
    alertUnsaved(false);
  },
  snapshot(): string {
    const d = this.data;
    return JSON.stringify([
      d.date,
      d.event,
      d.direction,
      d.kind,
      d.amount,
      d.giftName,
      d.note,
    ]);
  },
  dirty() {
    alertUnsaved(this.snapshot() !== this.original);
  },
  input(e: {
    currentTarget: { dataset: { field: string } };
    detail: { value: string };
  }) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
    this.dirty();
  },
  dateChange(e: { detail: { value: string } }) {
    this.setData({ date: e.detail.value });
    this.dirty();
  },
  directionChange(e: { currentTarget: { dataset: { value: GiftDirection } } }) {
    this.setData({ direction: e.currentTarget.dataset.value });
    this.dirty();
  },
  kindChange(e: { currentTarget: { dataset: { value: GiftKind } } }) {
    this.setData({ kind: e.currentTarget.dataset.value });
    this.dirty();
  },
  async save() {
    if (this.data.saving) return;
    if (!this.data.event.trim()) {
      toast("请输入往来事件");
      return;
    }
    const cost = amount(this.data.amount);
    if (cost === null) {
      toast("请输入有效金额");
      return;
    }
    if (this.data.kind === "礼物" && !this.data.giftName.trim()) {
      toast("请填写礼物名称");
      return;
    }
    this.setData({ saving: true });
    try {
      const value: GiftInput = {
        occurredAt: `${this.data.date}T12:00:00`,
        event: this.data.event.trim(),
        direction: this.data.direction,
        kind: this.data.kind,
        amount: cost,
        giftName: this.data.kind === "礼物" ? this.data.giftName.trim() : "",
        note: this.data.note.trim(),
      };
      if (this.data.id)
        await socialService.updateGift(
          this.data.contactId,
          this.data.id,
          value,
        );
      else await socialService.createGift(this.data.contactId, value);
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
