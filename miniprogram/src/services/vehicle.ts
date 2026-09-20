import { api } from "./http";
import type { PageResult, Vehicle, VehicleInput, VehicleStats } from "../types";
export const vehicleService = {
  list(page = 0): Promise<PageResult<Vehicle>> {
    return api(`/vehicles?page=${page}&size=20`);
  },
  stats(): Promise<VehicleStats> {
    return api("/vehicles/stats");
  },
  get(id: string): Promise<Vehicle> {
    return api(`/vehicles/${encodeURIComponent(id)}`);
  },
  create(value: VehicleInput): Promise<Vehicle> {
    return api("/vehicles", "POST", value);
  },
  update(id: string, value: VehicleInput): Promise<Vehicle> {
    return api(`/vehicles/${encodeURIComponent(id)}`, "PUT", value);
  },
  delete(id: string): Promise<void> {
    return api(`/vehicles/${encodeURIComponent(id)}`, "DELETE");
  },
};
