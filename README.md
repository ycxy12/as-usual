# 日常集 · 四个阶段已完成

微信原生小程序 + Spring Boot API。当前完成记录首页、每日碎片、车辆用车、居住成本、人情往来，以及物品首页、个人物品、衣柜、餐厅库与地点库。发现支持按预算、距离、分类和到访状态随机推荐；我的包含生活数据概览、分类、标签、数据管理和设置。

## 技术栈与目录

| 目录 | 内容 |
|---|---|
| `miniprogram/src/pages/record` | 记录首页 |
| `miniprogram/src/pages/{fragment,vehicle,housing,social}` | 四个记录模块的原生页面 |
| `miniprogram/src/pages/items` | 物品首页、个人物品及衣柜的列表、表单、详情 |
| `miniprogram/src/pages/discover` | 发现首页、餐厅/地点资料库、详情、表单与随机推荐 |
| `miniprogram/src/pages/profile` | 我的首页、分类、标签、数据管理和设置 |
| `miniprogram/src/components` | 空状态、统计卡片、费用分析 |
| `miniprogram/src/services` | 独立业务 Service 和认证 HTTP 客户端 |
| `backend/src/main/java/com/richangji` | Spring Boot 3.5 / Java 17 API |
| `backend/src/main/resources/db/migration` | Flyway 数据库迁移 |
| `docs/design/record-phase1.md` | 第一阶段记录设计 |
| `docs/design/asset-phase2.md` | 第二阶段物品设计与统计口径 |
| `docs/design/discover-phase3.md` | 第三阶段发现设计与推荐规则 |
| `docs/design/profile-phase4.md` | 第四阶段我的设计与全局管理规则 |

原生端使用 TypeScript、WXML、WXSS 和微信开发者工具。服务端使用 Spring JDBC、MySQL 8.4、Flyway。金额在数据库中为 `DECIMAL(12,2)`，服务端用 `BigDecimal`。客户端不把业务数据存入微信本地 Storage；Storage 仅缓存有期限的登录 token。

## 本地运行

1. 将 `.env.example` 复制为 `.env`，填写自己的微信小程序 AppID、AppSecret 和本地数据库密码。不要提交 `.env`。
2. 将 `miniprogram/project.config.json` 的 `appid` 改为同一个 AppID。
3. 启动 Docker Desktop，运行 `docker compose up --build -d`。API 健康检查：`http://127.0.0.1:8080/api/health`。首次启动 Flyway 自动建表。
4. 用微信开发者工具导入 `miniprogram/`。开发者工具本机调试使用 `miniprogram/src/config.ts` 里的 `http://127.0.0.1:8080/api/v1`。
5. 真机预览前，把 `miniprogram/src/config.ts` 改为已配置的小程序 HTTPS 合法域名，并将 API 部署到可访问的 HTTPS 服务。

可单独验证：`cd backend && mvn test`，以及 `cd miniprogram && npm install && npm run typecheck`。小程序的 TypeScript 由微信开发者工具 `useCompilerPlugins: ["typescript"]` 编译；npm 依赖仅用于静态类型检查。

## 数据与权限

小程序调用 `wx.login`；API 向微信换取 openid 后签发 30 天随机会话 token。所有记录、联系人、物品、衣物、餐厅、地点和图片都按该微信身份隔离。图片上传至 API 所在机器的 `IMAGE_DIR`，原图下载需要登录；Docker Compose 将数据库和图片分别放在持久卷。删除碎片、物品、衣物、餐厅或地点会删除其图片元数据及文件；放弃表单产生的临时图片由客户端主动删除，超过 24 小时的未关联图片会在后续上传时清理。

目前没有数据导出、导入、自动备份或复杂账号设置。部署前请规划 MySQL 和图片卷的备份；卸载小程序不会删除服务端数据。

## 协作流程

参考 `data-asset` 的 AI 流程：先查代码与需求证据；非平凡功能先更新当前设计，关键取舍再记 ADR；得到明确实施指令后改代码；用用户场景验证 API、存储与页面结果。这里保留轻量流程，不复制企业多模块架构。
