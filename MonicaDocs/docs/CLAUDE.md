# Claude 协作说明

## 项目范围

这里是 Monica 的独立 VitePress 主站源码。它负责产品介绍、指南、参考文档和生态页。

## 常用命令

从 `MonicaDocs` 目录运行：

```bash
npm run docs:dev
npm run docs:build
```

`npm run docs:dev` 默认在 `http://127.0.0.1:5173/` 运行文档站。
`npm run docs:build` 只构建文档站，输出到 `docs/.vitepress/dist`。

## 内容与路由

- 简体中文内容：`01.指南`、`02.配置`、`03.生态`。
- 多语言内容：`en`、`ja`、`ru`、`vi`。
- 首页：根目录 `index.md` 和各语言目录下的 `index.md`。
- 生态页：`03.生态/目录.md` 及 `.vitepress/theme/components/EcosystemLanding.vue`。
- 页面编辑链接与顶栏导航：`.vitepress/locales/*.mjs`。

## 修改原则

- 修改面向用户的功能、路由或导航时，检查所有语言版本是否需要同步。
- 优先复用 VitePress、Teek 和现有主题组件，不要引入新的前端框架。
- 生态页使用简洁的文档风格：避免大面积渐变、装饰性动画和过大的圆角；面板圆角使用 `8px`。
- 文档链接使用站内绝对路径，例如 `/reference/catalogue.html`；管理页始终使用 `/login`。
- `docs/.vitepress/dist` 和 `docs/.vitepress/cache` 是生成目录，禁止手动编辑。

## 验证

- 文档或主题变更后，至少确认开发页能正常加载。
- 合并前运行 `npm run docs:build`。
