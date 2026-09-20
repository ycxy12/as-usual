import { profileService } from "../../services/profile";
import { money, route } from "../../utils/format";

Page({
  data: {
    monthRecords: "0",
    items: "0",
    garments: "0",
    vehicle: "¥0.00",
    housing: "¥0.00",
    restaurants: "0",
    places: "0",
    error: "",
  },
  onShow() {
    void this.load();
  },
  async load() {
    try {
      const x = await profileService.overview();
      this.setData({
        monthRecords: String(x.monthRecords),
        items: String(x.items),
        garments: String(x.garments),
        vehicle: money(x.monthVehicleCost),
        housing: money(x.monthHousingCost),
        restaurants: String(x.visitedRestaurants),
        places: String(x.visitedPlaces),
        error: "",
      });
    } catch (error) {
      this.setData({
        error: error instanceof Error ? error.message : "加载失败",
      });
    }
  },
  onPullDownRefresh() {
    void this.load().finally(() => wx.stopPullDownRefresh());
  },
  open(e: WechatMiniprogram.BaseEvent) {
    route(`pages/profile/${e.currentTarget.dataset.page as string}`);
  },
});
