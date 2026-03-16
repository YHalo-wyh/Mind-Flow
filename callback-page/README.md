# MindFlow Callback Page

这是一个无需服务器后端的静态回调页。

## 快速部署（无服务器）

### 方案 1：GitHub Pages
1. 新建 GitHub 仓库并上传 `callback-page` 目录内容。
2. 仓库 Settings -> Pages。
3. Source 选择 `Deploy from a branch`，分支选 `main`，目录选 `/root`。
4. 等待生成地址，如 `https://<username>.github.io/<repo>/`。

### 方案 2：Cloudflare Pages
1. 登录 Cloudflare -> Pages -> Create a project。
2. 连接 Git 仓库，构建命令留空，输出目录填 `.`。
3. 发布后获得地址。

## Supabase 推荐配置

Authentication -> URL Configuration:
- Site URL: 回调页地址，例如 `https://<username>.github.io/<repo>/`
- Redirect URLs:
  - `https://<username>.github.io/<repo>/`
  - `mindflow://auth-callback`

## 说明
- 回调页会解析 URL hash 并尝试跳回 `mindflow://auth-callback`。
- 如果没有安装或未注册 deep link，页面会停留并显示参数内容。
