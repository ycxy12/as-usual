import { api } from "./http";
import type {
  CategoryModule,
  ProfileDataStats,
  ProfileOverview,
  TagView,
  UserCategory,
} from "../types";

export const profileService = {
  overview: () => api<ProfileOverview>("/profile/overview"),
  dataStats: () => api<ProfileDataStats>("/profile/data-stats"),
  clearData: () => api<void>("/profile/data", "DELETE"),
  categories: (module: CategoryModule) =>
    api<UserCategory[]>(`/profile/categories?module=${module}`),
  createCategory: (module: CategoryModule, name: string) =>
    api<UserCategory>(`/profile/categories?module=${module}`, "POST", { name }),
  renameCategory: (id: string, name: string) =>
    api<UserCategory>(`/profile/categories/${encodeURIComponent(id)}`, "PUT", {
      name,
    }),
  deleteCategory: (id: string) =>
    api<void>(`/profile/categories/${encodeURIComponent(id)}`, "DELETE"),
  tags: (q = "") => api<TagView[]>(`/profile/tags?q=${encodeURIComponent(q)}`),
  createTag: (name: string) => api<TagView>("/profile/tags", "POST", { name }),
  renameTag: (oldName: string, newName: string) =>
    api<TagView>("/profile/tags/rename", "POST", { oldName, newName }),
  deleteTag: (name: string) =>
    api<void>(`/profile/tags?name=${encodeURIComponent(name)}`, "DELETE"),
};
