import { api } from "./http";
import type { PageResult, Housing, HousingInput, HousingStats } from "../types";
export const housingService = {
  list(page = 0): Promise<PageResult<Housing>> {
    return api(`/housing?page=${page}&size=20`);
  },
  stats(): Promise<HousingStats> {
    return api("/housing/stats");
  },
  get(id: string): Promise<Housing> {
    return api(`/housing/${encodeURIComponent(id)}`);
  },
  create(value: HousingInput): Promise<Housing> {
    return api("/housing", "POST", value);
  },
  update(id: string, value: HousingInput): Promise<Housing> {
    return api(`/housing/${encodeURIComponent(id)}`, "PUT", value);
  },
  delete(id: string): Promise<void> {
    return api(`/housing/${encodeURIComponent(id)}`, "DELETE");
  },
};
