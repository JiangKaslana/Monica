# MonicaDocs 文档站

`MonicaDocs/docs` 是 Monica 的独立主站与文档源码。站点基于 VitePress 和
`vitepress-theme-teek` 构建，包含简体中文、英文、日文、俄文和越南文内容。

## 环境要求

- Node.js：建议使用当前项目依赖可兼容的 LTS 版本。
- npm：随 Node.js 安装即可。
- 依赖安装：项目根目录需要存在 `node_modules`。如果没有，请先执行：

```bash
npm install
```

## 本地开发

启动 VitePress 开发服务器：

```bash
npm run docs:dev
```

开发服务默认运行在：

```text
http://localhost:5173/Monica/
```

开发服务器支持热更新，修改 `docs` 下的 Markdown、主题组件或 `.vitepress` 配置后通常会自动刷新。

## 生产构建

在 `MonicaDocs` 目录执行：

```bash
npm run docs:build
```

最终部署目录为：

```text
MonicaDocs/docs/.vitepress/dist
```

构建完成后可以用于 GitHub Pages、静态服务器或其他静态托管平台。

## 本地预览构建产物

先完成生产构建，然后运行：

```bash
npm run docs:preview
```

预览服务会读取 `docs\.vitepress\dist` 中的静态产物。

## 目录结构

```text
docs
├─ .vitepress
│  ├─ config.mts                 VitePress 配置
│  ├─ locales                    多语言导航与编辑链接
│  └─ theme                      主题、组件与样式
├─ 01.指南                       简体中文指南
├─ 02.配置                       简体中文配置与参考文档
├─ 03.生态                       简体中文生态内容
├─ en                            英文内容
├─ ja                            日文内容
├─ ru                            俄文内容
├─ vi                            越南文内容
├─ index.md                      简体中文首页
├─ public                        静态资源
└─ CLAUDE.md                     Claude 协作说明
```

## 文档维护

- 新增中文内容时，优先放入 `01.指南`、`02.配置` 或 `03.生态` 对应目录。
- 多语言页面应保持与中文页面一致的路由和内容层级。
- 顶栏导航及“在 GitHub 上编辑此页”链接位于 `.vitepress/locales`。
- 生态页展示组件位于 `.vitepress/theme/components/EcosystemLanding.vue`。
- 首页的 `hero.actions` 位于各语言目录的 `index.md`。
- 不要手动编辑 `.vitepress/dist` 或 `.vitepress/cache`，它们由 VitePress 生成。
