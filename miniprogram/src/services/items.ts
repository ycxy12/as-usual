import { api } from "./http";
import type {
  Garment,
  GarmentInput,
  ItemStats,
  PageResult,
  PersonalItem,
  PersonalItemInput,
  WardrobeStats,
} from "../types";

function query(params: Record<string, string | number>): string {
  return (
    "?" +
    Object.entries(params)
      .map(
        ([key, value]) =>
          `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`,
      )
      .join("&")
  );
}
export const itemService = {
  list: (params: Record<string, string | number> = {}) =>
    api<PageResult<PersonalItem>>(`/items${query(params)}`),
  stats: () => api<ItemStats>("/items/stats"),
  get: (id: string) => api<PersonalItem>(`/items/${encodeURIComponent(id)}`),
  create: (data: PersonalItemInput) =>
    api<PersonalItem>("/items", "POST", data),
  update: (id: string, data: PersonalItemInput) =>
    api<PersonalItem>(`/items/${encodeURIComponent(id)}`, "PUT", data),
  delete: (id: string) =>
    api<void>(`/items/${encodeURIComponent(id)}`, "DELETE"),
};
export const wardrobeService = {
  list: (params: Record<string, string | number> = {}) =>
    api<PageResult<Garment>>(`/wardrobe${query(params)}`),
  stats: () => api<WardrobeStats>("/wardrobe/stats"),
  get: (id: string) => api<Garment>(`/wardrobe/${encodeURIComponent(id)}`),
  create: (data: GarmentInput) => api<Garment>("/wardrobe", "POST", data),
  update: (id: string, data: GarmentInput) =>
    api<Garment>(`/wardrobe/${encodeURIComponent(id)}`, "PUT", data),
  wear: (id: string) =>
    api<Garment>(`/wardrobe/${encodeURIComponent(id)}/wear`, "POST"),
  delete: (id: string) =>
    api<void>(`/wardrobe/${encodeURIComponent(id)}`, "DELETE"),
};
