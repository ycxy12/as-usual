import { api } from "./http";
import type {
  LeisurePlace,
  LeisurePlaceInput,
  PageResult,
  PlaceStats,
  Restaurant,
  RestaurantInput,
  RestaurantStats,
} from "../types";

function query(
  params: Record<string, string | number | boolean | null | undefined>,
): string {
  const values = Object.entries(params).filter(
    ([, value]) => value !== "" && value !== null && value !== undefined,
  );
  return values.length
    ? "?" +
        values
          .map(
            ([key, value]) =>
              `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`,
          )
          .join("&")
    : "";
}
export const restaurantService = {
  list: (
    params: Record<string, string | number | boolean | null | undefined> = {},
  ) => api<PageResult<Restaurant>>(`/restaurants${query(params)}`),
  stats: () => api<RestaurantStats>("/restaurants/stats"),
  recommend: (
    params: Record<string, string | number | boolean | null | undefined>,
  ) => api<Restaurant>(`/restaurants/recommend${query(params)}`),
  get: (id: string) =>
    api<Restaurant>(`/restaurants/${encodeURIComponent(id)}`),
  create: (data: RestaurantInput) =>
    api<Restaurant>("/restaurants", "POST", data),
  update: (id: string, data: RestaurantInput) =>
    api<Restaurant>(`/restaurants/${encodeURIComponent(id)}`, "PUT", data),
  visit: (id: string) =>
    api<Restaurant>(`/restaurants/${encodeURIComponent(id)}/visit`, "POST"),
  delete: (id: string) =>
    api<void>(`/restaurants/${encodeURIComponent(id)}`, "DELETE"),
};
export const placeService = {
  list: (
    params: Record<string, string | number | boolean | null | undefined> = {},
  ) => api<PageResult<LeisurePlace>>(`/places${query(params)}`),
  stats: () => api<PlaceStats>("/places/stats"),
  recommend: (
    params: Record<string, string | number | boolean | null | undefined>,
  ) => api<LeisurePlace>(`/places/recommend${query(params)}`),
  get: (id: string) => api<LeisurePlace>(`/places/${encodeURIComponent(id)}`),
  create: (data: LeisurePlaceInput) =>
    api<LeisurePlace>("/places", "POST", data),
  update: (id: string, data: LeisurePlaceInput) =>
    api<LeisurePlace>(`/places/${encodeURIComponent(id)}`, "PUT", data),
  visit: (id: string) =>
    api<LeisurePlace>(`/places/${encodeURIComponent(id)}/visit`, "POST"),
  delete: (id: string) =>
    api<void>(`/places/${encodeURIComponent(id)}`, "DELETE"),
};
