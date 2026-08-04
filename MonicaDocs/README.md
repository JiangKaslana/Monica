# Monica 文档站仓库迁移说明

为方便后续生态建设，文档站已从 `Monica` 主仓库的 `MonicaDocs/` 子目录迁出，作为独立仓库维护。

## 新仓库位置

新文档仓库位于 [`Monica-Pass/MonicaDocs`](https://github.com/Monica-Pass/MonicaDocs)。

## 迁移内容

文档内容、VitePress 配置、静态资源、构建脚本、`package.json`、
`package-lock.json` 和 Node.js 依赖均应位于新文档仓库的根目录。

迁移后的目录结构不再包含嵌套的 `docs/`：

```text
MonicaDocs/
├── .vitepress/
├── public/
├── scripts/
├── package.json
├── package-lock.json
├── index.md
└── personal.md
```

## 本地开发

在新文档仓库根目录执行：

```bash
npm ci
npm run docs:dev
npm run docs:build
npm run docs:preview
```

构建产物位于 `.vitepress/dist/`。

## GitHub Actions 迁移

以下工作流应在新文档仓库中运行：

| 工作流 | 职责 |
| --- | --- |
| `deploy-website.yml` | 构建 `.vitepress/dist/` 并部署到 GitHub Pages。 |
| `update-github-commits.yml` | 获取 `Monica-Pass/Monica` 的提交数据，更新 `public/github-commits.json`。 |

## 所需变量与密钥

### 原 `Monica` 主仓库

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| Secret | `AFDIAN_TOKEN` | 调用爱发电开放 API。 |
| Variable | `DOCS_REPOSITORY` | 新文档仓库，格式为 `owner/repo`。 |
| Secret | `DOCS_REPO_TOKEN` | 对新文档仓库具有 `Contents: Read and write` 权限的 fine-grained PAT 或 GitHub App Token。 |

### 新文档仓库

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| Secret | `MONICA_SOURCE_REPO_TOKEN` | 对 `Monica-Pass/Monica` 具有 `Contents: Read` 权限，用于读取提交数据。 |

GitHub Actions 自动提供的 `GITHUB_TOKEN` 负责提交工作流生成的文件；请确保
对应工作流拥有 `contents: write` 权限。