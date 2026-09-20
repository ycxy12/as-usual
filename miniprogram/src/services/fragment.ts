import { api } from "./http";
import type { Fragment, FragmentInput, PageResult } from "../types";
export const fragmentService = {
  list(page = 0, q = "", tag = "", date = ""): Promise<PageResult<Fragment>> {
    return api(
      `/fragments?page=${page}&size=20&q=${encodeURIComponent(q)}&tag=${encodeURIComponent(tag)}&date=${encodeURIComponent(date)}`,
    );
  },
  tags(): Promise<string[]> {
    return api("/fragments/tags");
  },
  get(id: string): Promise<Fragment> {
    return api(`/fragments/${encodeURIComponent(id)}`);
  },
  create(value: FragmentInput): Promise<Fragment> {
    return api("/fragments", "POST", value);
  },
  update(id: string, value: FragmentInput): Promise<Fragment> {
    return api(`/fragments/${encodeURIComponent(id)}`, "PUT", value);
  },
  delete(id: string): Promise<void> {
    return api(`/fragments/${encodeURIComponent(id)}`, "DELETE");
  },
};
