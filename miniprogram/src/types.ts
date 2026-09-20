export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface Core {
  id: string;
  createdAt: string;
  updatedAt: string;
}
export interface Fragment extends Core {
  content: string;
  occurredAt: string;
  tags: string[];
  imageIds: string[];
}
export interface FragmentInput {
  content: string;
  occurredAt: string;
  tags: string[];
  imageIds: string[];
}
export const vehicleCategories = [
  "加油",
  "充电",
  "停车",
  "洗车",
  "保养",
  "维修",
  "保险",
  "违章",
  "其他费用",
] as const;
export type VehicleCategory = string;
export interface Vehicle extends Core {
  occurredAt: string;
  category: VehicleCategory;
  amount: number;
  odometer: number | null;
  quantity: number | null;
  unitPrice: number | null;
  location: string;
  note: string;
}
export type VehicleInput = Omit<Vehicle, keyof Core>;
export const housingCategories = [
  "房租 / 房贷",
  "物业",
  "水费",
  "电费",
  "燃气",
  "宽带",
  "停车费",
  "家具",
  "家电",
  "维修",
  "其他",
] as const;
export type HousingCategory = string;
export interface Housing extends Core {
  occurredAt: string;
  category: HousingCategory;
  amount: number;
  billingMonth: string;
  note: string;
}
export type HousingInput = Omit<Housing, keyof Core>;
export interface Breakdown {
  label: string;
  amount: number;
  percent?: number;
}
export interface ExpenseSummary {
  month: number;
  year: number;
  total: number;
  monthlyAverage: number;
  categories: Breakdown[];
  months: Breakdown[];
}
export interface VehicleStats {
  expenses: ExpenseSummary;
  distance: number;
  costPerKm: number;
  mileage: { occurredAt: string; odometer: number }[];
}
export interface HousingStats {
  expenses: ExpenseSummary;
  dailyAverage: number;
}
export interface Contact extends Core {
  name: string;
  relation: string;
  note: string;
  sent: number;
  received: number;
}
export interface ContactInput {
  name: string;
  relation: string;
  note: string;
}
export type GiftDirection = "送出" | "收到";
export type GiftKind = "现金" | "礼物";
export interface Gift extends Core {
  contactId: string;
  contactName: string;
  occurredAt: string;
  event: string;
  direction: GiftDirection;
  kind: GiftKind;
  amount: number;
  giftName: string;
  note: string;
}
export interface GiftInput {
  occurredAt: string;
  event: string;
  direction: GiftDirection;
  kind: GiftKind;
  amount: number;
  giftName: string;
  note: string;
}

export const itemCategories = [
  "数码产品",
  "摄影器材",
  "家电",
  "家具",
  "工具",
  "收藏品",
  "其他",
] as const;
export const itemStatuses = ["在用", "闲置", "已转让", "已损坏"] as const;
export interface PersonalItem extends Core {
  name: string;
  category: string;
  brand: string | null;
  model: string | null;
  purchaseDate: string | null;
  purchasePrice: number | null;
  currentValue: number | null;
  warrantyUntil: string | null;
  status: string;
  storageLocation: string | null;
  note: string | null;
  tags: string[];
  imageId: string | null;
  heldDays: number | null;
  depreciation: number | null;
  dailyCost: number | null;
  warrantyActive: boolean | null;
}
export type PersonalItemInput = Pick<
  PersonalItem,
  | "name"
  | "category"
  | "brand"
  | "model"
  | "purchaseDate"
  | "purchasePrice"
  | "currentValue"
  | "warrantyUntil"
  | "status"
  | "storageLocation"
  | "note"
  | "tags"
  | "imageId"
>;
export interface ItemStats {
  total: number;
  purchaseTotal: number;
  currentTotal: number;
  categories: Record<string, number>;
}
export const garmentCategories = [
  "上衣",
  "裤装",
  "外套",
  "鞋",
  "包",
  "配饰",
] as const;
export const garmentStatuses = ["在穿", "闲置", "已处理"] as const;
export const garmentSeasons = ["", "春", "夏", "秋", "冬", "四季"] as const;
export interface Garment extends Core {
  name: string;
  category: string;
  brand: string | null;
  color: string | null;
  size: string | null;
  season: string | null;
  purchaseDate: string | null;
  purchasePrice: number | null;
  wearCount: number;
  lastWornDate: string | null;
  status: string;
  tags: string[];
  imageId: string | null;
  costPerWear: number | null;
  longUnworn: boolean;
}
export type GarmentInput = Pick<
  Garment,
  | "name"
  | "category"
  | "brand"
  | "color"
  | "size"
  | "season"
  | "purchaseDate"
  | "purchasePrice"
  | "wearCount"
  | "lastWornDate"
  | "status"
  | "tags"
  | "imageId"
>;
export interface WardrobeStats {
  total: number;
  categories: Record<string, number>;
  longUnworn: number;
  wearRanking: { id: string; name: string; wearCount: number }[];
}

export const restaurantCuisines = [
  "中餐",
  "火锅",
  "烧烤",
  "面食",
  "日料",
  "西餐",
  "咖啡甜品",
  "其他",
] as const;
export const placeCategories = [
  "景点",
  "公园",
  "商场",
  "咖啡店",
  "展览",
  "古镇",
  "徒步",
  "自驾",
  "亲子",
  "摄影地点",
  "其他",
] as const;
export interface Restaurant extends Core {
  name: string;
  cuisine: string;
  averagePrice: number | null;
  address: string | null;
  distanceKm: number | null;
  rating: number | null;
  recommendedDishes: string | null;
  avoidedDishes: string | null;
  lastVisitedDate: string | null;
  visitCount: number;
  note: string | null;
  tags: string[];
  imageId: string | null;
}
export type RestaurantInput = Pick<
  Restaurant,
  | "name"
  | "cuisine"
  | "averagePrice"
  | "address"
  | "distanceKm"
  | "rating"
  | "recommendedDishes"
  | "avoidedDishes"
  | "lastVisitedDate"
  | "visitCount"
  | "note"
  | "tags"
  | "imageId"
>;
export interface RestaurantStats {
  total: number;
  visited: number;
  neverVisited: number;
}
export interface LeisurePlace extends Core {
  name: string;
  category: string;
  address: string | null;
  distanceKm: number | null;
  averageCost: number | null;
  recommendation: number | null;
  visitStatus: "去过" | "想去";
  lastVisitedDate: string | null;
  visitCount: number;
  note: string | null;
  tags: string[];
  imageId: string | null;
}
export type LeisurePlaceInput = Pick<
  LeisurePlace,
  | "name"
  | "category"
  | "address"
  | "distanceKm"
  | "averageCost"
  | "recommendation"
  | "visitStatus"
  | "lastVisitedDate"
  | "visitCount"
  | "note"
  | "tags"
  | "imageId"
>;
export interface PlaceStats {
  total: number;
  visited: number;
  wishList: number;
}
export type VisitFilter = "ANY" | "VISITED" | "NEVER";

export type CategoryModule =
  "item" | "wardrobe" | "vehicle" | "housing" | "restaurant" | "place";
export interface UserCategory {
  id: string;
  module: CategoryModule;
  name: string;
  sortOrder: number;
}
export interface TagView {
  name: string;
  usageCount: number;
}
export interface ProfileOverview {
  monthRecords: number;
  items: number;
  garments: number;
  monthVehicleCost: number;
  monthHousingCost: number;
  visitedRestaurants: number;
  visitedPlaces: number;
}
export interface ProfileDataStats {
  fragments: number;
  vehicles: number;
  housing: number;
  contacts: number;
  gifts: number;
  items: number;
  garments: number;
  restaurants: number;
  places: number;
  images: number;
}
