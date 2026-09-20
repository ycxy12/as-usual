import { API_BASE_URL } from "../config";

const TOKEN_KEY = "richangji:session:v1";
interface Session {
  token: string;
  expiresAt: string;
}
interface ApiError {
  message?: string;
}
let loginInFlight: Promise<string> | null = null;

function requestRaw<T>(
  url: string,
  method: "GET" | "POST" | "PUT" | "DELETE",
  data?: unknown,
  token?: string,
): Promise<{ status: number; data: T | ApiError }> {
  return new Promise((resolve, reject) =>
    wx.request({
      url: API_BASE_URL + url,
      method,
      data: data as Record<string, unknown> | undefined,
      header: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      success: (response) =>
        resolve({
          status: response.statusCode,
          data: response.data as T | ApiError,
        }),
      fail: reject,
    }),
  );
}

async function login(): Promise<string> {
  const code = await new Promise<string>((resolve, reject) =>
    wx.login({
      success: (r) =>
        r.code ? resolve(r.code) : reject(new Error("微信登录未返回凭证")),
      fail: reject,
    }),
  );
  const response = await requestRaw<Session>("/auth/wechat", "POST", { code });
  if (response.status !== 200)
    throw new Error((response.data as ApiError).message || "登录失败");
  const session = response.data as Session;
  wx.setStorageSync(TOKEN_KEY, session);
  return session.token;
}

export async function token(force = false): Promise<string> {
  const saved = wx.getStorageSync(TOKEN_KEY) as Session | "";
  if (
    !force &&
    saved &&
    saved.token &&
    Date.parse(saved.expiresAt) > Date.now() + 60_000
  )
    return saved.token;
  if (!loginInFlight)
    loginInFlight = login().finally(() => {
      loginInFlight = null;
    });
  return loginInFlight;
}

export async function api<T>(
  path: string,
  method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
  data?: unknown,
): Promise<T> {
  let auth = await token();
  let response = await requestRaw<T>(path, method, data, auth);
  if (response.status === 401) {
    wx.removeStorageSync(TOKEN_KEY);
    auth = await token(true);
    response = await requestRaw<T>(path, method, data, auth);
  }
  if (response.status < 200 || response.status >= 300)
    throw new Error(
      (response.data as ApiError).message || `请求失败（${response.status}）`,
    );
  return response.data as T;
}

export async function uploadImage(path: string): Promise<string> {
  const auth = await token();
  return new Promise((resolve, reject) =>
    wx.uploadFile({
      url: API_BASE_URL + "/images",
      filePath: path,
      name: "file",
      header: { Authorization: `Bearer ${auth}` },
      success: (response) => {
        try {
          const parsed = JSON.parse(response.data) as {
            id?: string;
            message?: string;
          };
          if (response.statusCode === 201 && parsed.id) resolve(parsed.id);
          else reject(new Error(parsed.message || "图片上传失败"));
        } catch {
          reject(new Error("图片上传响应无效"));
        }
      },
      fail: reject,
    }),
  );
}

export function deleteImage(id: string): Promise<void> {
  return api<void>(`/images/${encodeURIComponent(id)}`, "DELETE");
}

export async function downloadImage(id: string): Promise<string> {
  const auth = await token();
  return new Promise((resolve, reject) =>
    wx.downloadFile({
      url: `${API_BASE_URL}/images/${encodeURIComponent(id)}`,
      header: { Authorization: `Bearer ${auth}` },
      success: (response) =>
        response.statusCode === 200
          ? resolve(response.tempFilePath)
          : reject(new Error("图片下载失败")),
      fail: reject,
    }),
  );
}
