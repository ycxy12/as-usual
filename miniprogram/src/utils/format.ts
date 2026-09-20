export function localDate(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
export function localTime(): string {
  const d = new Date();
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function pad(n: number): string {
  return String(n).padStart(2, "0");
}
export function dateDisplay(s: string): string {
  return s.slice(0, 10).replace(/-/g, ".");
}
export function timeDisplay(s: string): string {
  return s.slice(11, 16);
}
export function money(n: number): string {
  return `¥${Number(n || 0).toFixed(2)}`;
}
export function amount(s: string): number | null {
  if (!/^\d+(\.\d{1,2})?$/.test(s.trim())) return null;
  return Number(s);
}
export function toast(title: string): void {
  wx.showToast({ title, icon: "none", duration: 1600 });
}
export function route(path: string, params: Record<string, string> = {}): void {
  const query = Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join("&");
  wx.navigateTo({ url: `/${path}${query ? "?" + query : ""}` });
}
export function confirmDelete(
  content = "删除后无法恢复，确定删除吗？",
): Promise<boolean> {
  return new Promise((resolve) =>
    wx.showModal({
      title: "确认删除",
      content,
      confirmColor: "#ac5a50",
      success: (r) => resolve(r.confirm),
      fail: () => resolve(false),
    }),
  );
}
export function alertUnsaved(enabled: boolean): void {
  if (enabled)
    wx.enableAlertBeforeUnload({ message: "尚未保存，确定离开吗？" });
  else wx.disableAlertBeforeUnload();
}
