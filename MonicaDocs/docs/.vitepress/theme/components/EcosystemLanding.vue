<script setup lang="ts">
import { computed } from "vue";
import { useData, withBase } from "vitepress";

type LocaleKey = "root" | "en" | "ja" | "ru" | "vi";

type ClientCard = {
  key: "android" | "ios" | "windows";
  title: string;
  kicker: string;
  description: string;
  accent: string;
  href: string;
};

type EcosystemCopy = {
  eyebrow: string;
  title: string;
  lead: string;
  primaryCta: string;
  secondaryCta: string;
  metrics: Array<{ value: string; label: string }>;
  cards: ClientCard[];
  principlesTitle: string;
  readMore: string;
  principles: Array<{ title: string; description: string }>;
};

const { page } = useData();

const localeKey = computed<LocaleKey>(() => {
  const relativePath = page.value.relativePath;
  if (relativePath.startsWith("en/")) return "en";
  if (relativePath.startsWith("ja/")) return "ja";
  if (relativePath.startsWith("ru/")) return "ru";
  if (relativePath.startsWith("vi/")) return "vi";
  return "root";
});

const localePrefix = computed(() => (localeKey.value === "root" ? "" : `/${localeKey.value}`));
const pageLink = (path: string) => withBase(`${localePrefix.value}${path}`);

const copy: Record<LocaleKey, EcosystemCopy> = {
  root: {
    eyebrow: "Monica Ecosystem",
    title: "一个本地优先的密码管理生态",
    lead: "从 Android、iOS 到 Windows，Monica 把安全、可迁移和跨端协作放在同一张地图上。这里是所有客户端与技术路线的入口。",
    primaryCta: "查看 Android 客户端",
    secondaryCta: "阅读技术文档",
    metrics: [
      { value: "3", label: "客户端方向" },
      { value: "Local", label: "本地优先" },
      { value: "MDBX", label: "数据加密" },
    ],
    cards: [
      {
        key: "android",
        title: "Monica for Android",
        kicker: "Android",
        description: "聚合 Bitwarden 同步能力与 KeePass 数据兼容，面向日常高频密码管理。",
        accent: "#23b3a4",
        href: "/ecosystem/AndroidReadme",
      },
      {
        key: "ios",
        title: "Monica for iOS",
        kicker: "iOS",
        description: "围绕本地保险库、恢复性和系统体验，规划 iPhone 上的安全密码管理。",
        accent: "#4f8cff",
        href: "/ecosystem/iOSReadme",
      },
      {
        key: "windows",
        title: "Monica by Avalonia",
        kicker: "跨桌面平台",
        description: "Monica 密码库的桌面端实现。",
        accent: "#f0a32f",
        href: "/ecosystem/WindowsReadme",
      },
    ],
    principlesTitle: "设计原则",
    readMore: "查看详情",
    principles: [
      { title: "本地可控", description: "核心数据优先掌握在用户设备中，减少对单一云端的依赖。" },
      { title: "开放迁移", description: "兼容成熟密码库生态，加密数据提供基于硬件的安全隔离边界。" },
      { title: "跨端一致", description: "不同平台保留原生体验，同时提供本地安全与同步模型。" },
    ],
  },
  en: {
    eyebrow: "Monica Ecosystem",
    title: "A local-first password ecosystem",
    lead: "From Android and iOS to Windows, Monica connects security, portability, and cross-device workflows in one map. This is the gateway to every client and technical track.",
    primaryCta: "Explore Android",
    secondaryCta: "Read the docs",
    metrics: [
      { value: "3", label: "Client tracks" },
      { value: "Local", label: "First by design" },
      { value: "MDBX", label: "Data encryption" },
    ],
    cards: [
      {
        key: "android",
        title: "Monica for Android",
        kicker: "Android",
        description: "Combines Bitwarden-style sync with KeePass data compatibility for everyday password workflows.",
        accent: "#23b3a4",
        href: "/ecosystem/AndroidReadme",
      },
      {
        key: "ios",
        title: "Monica for iOS",
        kicker: "iOS",
        description: "Plans a secure iPhone experience around local vaults, recoverability, and native platform behavior.",
        accent: "#4f8cff",
        href: "/ecosystem/iOSReadme",
      },
      {
        key: "windows",
        title: "Monica by Avalonia",
        kicker: "Cross-desktop platform",
        description: "The desktop implementation of the Monica password vault.",
        accent: "#f0a32f",
        href: "/ecosystem/WindowsReadme",
      },
    ],
    principlesTitle: "Design Principles",
    readMore: "Read more",
    principles: [
      { title: "Local control", description: "Core vault data stays primarily under the user's control on their own devices." },
      { title: "Open migration", description: "Compatibility with mature vault ecosystems keeps importing and leaving humane." },
      { title: "Cross-platform consistency", description: "Each client feels native while sharing one security and sync model." },
    ],
  },
  ja: {
    eyebrow: "Monica Ecosystem",
    title: "ローカルファーストのパスワード管理エコシステム",
    lead: "Android、iOS、Windows をつなぎ、安全性、移行性、クロスデバイス運用を一枚の地図にまとめます。ここが各クライアントと技術文書への入口です。",
    primaryCta: "Android を見る",
    secondaryCta: "技術文書を読む",
    metrics: [
      { value: "3", label: "クライアント" },
      { value: "Local", label: "ローカル優先" },
      { value: "MDBX", label: "データ暗号化" },
    ],
    cards: [
      {
        key: "android",
        title: "Monica for Android",
        kicker: "Android",
        description: "Bitwarden 風の同期と KeePass データ互換性を組み合わせ、日常的なパスワード管理を支えます。",
        accent: "#23b3a4",
        href: "/ecosystem/AndroidReadme",
      },
      {
        key: "ios",
        title: "Monica for iOS",
        kicker: "iOS",
        description: "ローカル保管庫、復旧性、ネイティブ体験を軸に iPhone 向けの安全な管理体験を設計します。",
        accent: "#4f8cff",
        href: "/ecosystem/iOSReadme",
      },
      {
        key: "windows",
        title: "Monica by Avalonia",
        kicker: "クロスデスクトッププラットフォーム",
        description: "Monica パスワード保管庫のデスクトップ実装。",
        accent: "#f0a32f",
        href: "/ecosystem/WindowsReadme",
      },
    ],
    principlesTitle: "設計原則",
    readMore: "詳しく見る",
    principles: [
      { title: "ローカル制御", description: "中核データはユーザー自身の端末で管理できることを優先します。" },
      { title: "開かれた移行", description: "成熟したパスワード管理エコシステムとの互換性を重視します。" },
      { title: "クロス端末の一貫性", description: "各端末の自然な体験と共通の安全モデルを両立します。" },
    ],
  },
  ru: {
    eyebrow: "Monica Ecosystem",
    title: "Локальная экосистема управления паролями",
    lead: "Android, iOS и Windows объединяются вокруг безопасности, переносимости данных и понятных рабочих процессов. Здесь собраны входы во все клиенты и технические направления.",
    primaryCta: "Открыть Android",
    secondaryCta: "Читать документацию",
    metrics: [
      { value: "3", label: "Клиента" },
      { value: "Local", label: "Локальный подход" },
      { value: "MDBX", label: "Шифрование данных" },
    ],
    cards: [
      {
        key: "android",
        title: "Monica for Android",
        kicker: "Android",
        description: "Сочетает синхронизацию в стиле Bitwarden и совместимость с данными KeePass для ежедневной работы.",
        accent: "#23b3a4",
        href: "/ecosystem/AndroidReadme",
      },
      {
        key: "ios",
        title: "Monica for iOS",
        kicker: "iOS",
        description: "Проектирует безопасный опыт на iPhone вокруг локального хранилища, восстановления и нативного поведения.",
        accent: "#4f8cff",
        href: "/ecosystem/iOSReadme",
      },
      {
        key: "windows",
        title: "Monica by Avalonia",
        kicker: "Кроссплатформенная настольная платформа",
        description: "Настольная реализация хранилища паролей Monica.",
        accent: "#f0a32f",
        href: "/ecosystem/WindowsReadme",
      },
    ],
    principlesTitle: "Принципы",
    readMore: "Подробнее",
    principles: [
      { title: "Локальный контроль", description: "Основные данные остаются под контролем пользователя на его устройствах." },
      { title: "Открытая миграция", description: "Совместимость с зрелыми экосистемами делает перенос данных спокойнее." },
      { title: "Единая модель", description: "Клиенты остаются нативными, но разделяют общую модель безопасности и синхронизации." },
    ],
  },
  vi: {
    eyebrow: "Monica Ecosystem",
    title: "Hệ sinh thái mật khẩu ưu tiên cục bộ",
    lead: "Từ Android, iOS đến Windows, Monica kết nối bảo mật, khả năng di chuyển dữ liệu và quy trình đa thiết bị trong cùng một bản đồ.",
    primaryCta: "Xem Android",
    secondaryCta: "Đọc tài liệu",
    metrics: [
      { value: "3", label: "Hướng client" },
      { value: "Local", label: "Ưu tiên cục bộ" },
      { value: "MDBX", label: "Mã hóa dữ liệu" },
    ],
    cards: [
      {
        key: "android",
        title: "Monica for Android",
        kicker: "Android",
        description: "Kết hợp đồng bộ kiểu Bitwarden với khả năng tương thích dữ liệu KeePass cho nhu cầu hằng ngày.",
        accent: "#23b3a4",
        href: "/ecosystem/AndroidReadme",
      },
      {
        key: "ios",
        title: "Monica for iOS",
        kicker: "iOS",
        description: "Định hình trải nghiệm iPhone an toàn quanh kho cục bộ, khả năng khôi phục và hành vi gốc của nền tảng.",
        accent: "#4f8cff",
        href: "/ecosystem/iOSReadme",
      },
      {
        key: "windows",
        title: "Monica by Avalonia",
        kicker: "Nền tảng đa desktop",
        description: "Bản triển khai desktop của kho mật khẩu Monica.",
        accent: "#f0a32f",
        href: "/ecosystem/WindowsReadme",
      },
    ],
    principlesTitle: "Nguyên tắc thiết kế",
    readMore: "Xem thêm",
    principles: [
      { title: "Kiểm soát cục bộ", description: "Dữ liệu lõi ưu tiên nằm dưới quyền kiểm soát của người dùng trên thiết bị riêng." },
      { title: "Di chuyển mở", description: "Tương thích với các hệ sinh thái trưởng thành để việc nhập và rời đi nhẹ nhàng hơn." },
      { title: "Nhất quán đa nền tảng", description: "Mỗi client vẫn tự nhiên trên nền tảng của mình nhưng chia sẻ cùng mô hình bảo mật." },
    ],
  },
};

const currentCopy = computed(() => copy[localeKey.value]);
const cards = computed(() => currentCopy.value.cards);
</script>

<template>
  <section class="ecosystem-landing" aria-labelledby="ecosystem-title">
    <header class="ecosystem-hero">
      <p class="ecosystem-hero__eyebrow">{{ currentCopy.eyebrow }}</p>
      <h1 id="ecosystem-title">{{ currentCopy.title }}</h1>
      <p class="ecosystem-hero__lead">{{ currentCopy.lead }}</p>
      <div class="ecosystem-hero__actions">
        <a class="ecosystem-button ecosystem-button--primary" :href="pageLink('/ecosystem/AndroidReadme')">
          {{ currentCopy.primaryCta }}
        </a>
        <a class="ecosystem-button ecosystem-button--ghost" :href="pageLink('/reference/catalogue')">
          {{ currentCopy.secondaryCta }}
        </a>
      </div>
    </header>

    <div class="ecosystem-metrics" aria-label="Ecosystem summary">
      <div v-for="metric in currentCopy.metrics" :key="metric.label" class="ecosystem-metric">
        <strong>{{ metric.value }}</strong>
        <span>{{ metric.label }}</span>
      </div>
    </div>

    <div class="ecosystem-cards">
      <a
        v-for="card in cards"
        :key="card.key"
        class="ecosystem-card"
        :href="pageLink(card.href)"
        :style="{ '--card-accent': card.accent }"
      >
        <div class="ecosystem-card__meta">
          <span class="ecosystem-card__kicker">{{ card.kicker }}</span>
        </div>
        <h2>{{ card.title }}</h2>
        <p>{{ card.description }}</p>
        <span class="ecosystem-card__arrow">{{ currentCopy.readMore }}</span>
      </a>
    </div>

    <section class="ecosystem-principles" :aria-label="currentCopy.principlesTitle">
      <h2>{{ currentCopy.principlesTitle }}</h2>
      <div class="ecosystem-principles__grid">
        <article v-for="principle in currentCopy.principles" :key="principle.title">
          <h3>{{ principle.title }}</h3>
          <p>{{ principle.description }}</p>
        </article>
      </div>
    </section>
  </section>
</template>

<style>
@layer monica-ecosystem {
  @scope (.ecosystem-landing) {
    :scope {
      --eco-bg: light-dark(oklch(98% 0.018 172deg), oklch(16% 0.024 246deg));
      --eco-panel: light-dark(oklch(100% 0.012 172deg / 90%), oklch(23% 0.034 246deg / 80%));
      --eco-panel-hard: light-dark(oklch(96% 0.018 172deg), oklch(20% 0.03 246deg));
      --eco-ink: light-dark(oklch(24% 0.04 246deg), oklch(96% 0.012 95deg));
      --eco-muted: light-dark(oklch(48% 0.045 246deg), oklch(78% 0.024 246deg));
      --eco-line: light-dark(oklch(84% 0.034 190deg), oklch(43% 0.04 246deg));
      --eco-green: oklch(64% 0.16 174deg);
      --eco-blue: oklch(62% 0.18 252deg);
      --eco-gold: oklch(73% 0.17 78deg);
      --eco-radius: clamp(1.25rem, 0.95rem + 1.5cqi, 2.35rem);
      --eco-gap: clamp(0.9rem, 0.68rem + 1.1cqi, 1.65rem);
      --eco-pad: clamp(1.1rem, 0.56rem + 2.7cqi, 3rem);
      color-scheme: light dark;
      container: ecosystem / inline-size;
      position: relative;
      display: grid;
      gap: var(--eco-gap);
      margin-block: clamp(0.25rem, 0.1rem + 0.75cqi, 1rem) clamp(2.5rem, 1.5rem + 5cqi, 5rem);
      margin-inline: auto;
      max-inline-size: min(100%, 72rem);
      color: var(--eco-ink);
      isolation: isolate;
    }

    :scope::before {
      position: absolute;
      inset-block-start: 0;
      inset-inline: 50% auto;
      z-index: -1;
      inline-size: min(82rem, 96vi);
      block-size: min(46rem, 72cqi);
      border-radius: 50%;
      background:
        radial-gradient(circle at 18% 20%, oklch(72% 0.16 174deg / 24%), transparent 32%),
        radial-gradient(circle at 80% 18%, oklch(66% 0.18 252deg / 22%), transparent 30%),
        radial-gradient(circle at 58% 76%, oklch(78% 0.17 78deg / 18%), transparent 35%);
      content: "";
      filter: blur(1rem);
      translate: -50% 0;
    }

    * {
      box-sizing: border-box;
    }

    h1,
    h2,
    h3,
    p {
      margin: 0;
    }

    h1,
    h2,
    h3 {
      text-wrap: balance;
    }

    p {
      text-wrap: pretty;
    }

    a {
      color: inherit;
      text-decoration: none;
    }

    .ecosystem-hero {
      display: grid;
      gap: clamp(1.25rem, 0.9rem + 1.75cqi, 2.6rem);
      align-items: stretch;
      overflow: clip;
      border: 1px solid color-mix(in oklch, var(--eco-line), transparent 22%);
      border-radius: calc(var(--eco-radius) + 0.35rem);
      padding: var(--eco-pad);
      background:
        linear-gradient(145deg, var(--eco-panel), color-mix(in oklch, var(--eco-bg), transparent 10%)),
        radial-gradient(circle at 0% 0%, oklch(73% 0.15 174deg / 13%), transparent 44%);
      box-shadow: 0 1.6rem 5rem oklch(15% 0.035 246deg / 14%);
    }

    .ecosystem-hero__copy {
      display: grid;
      align-content: center;
      gap: clamp(0.9rem, 0.68rem + 1.1cqi, 1.45rem);
    }

    .ecosystem-hero__eyebrow {
      inline-size: fit-content;
      border: 1px solid color-mix(in oklch, var(--eco-green), transparent 46%);
      border-radius: 100vi;
      padding-block: clamp(0.32rem, 0.24rem + 0.4cqi, 0.5rem);
      padding-inline: clamp(0.65rem, 0.5rem + 0.75cqi, 0.95rem);
      background: color-mix(in oklch, var(--eco-green), transparent 90%);
      color: color-mix(in oklch, var(--eco-green), var(--eco-ink) 18%);
      font-size: clamp(0.72rem, 0.68rem + 0.2cqi, 0.82rem);
      font-weight: 850;
      letter-spacing: 0.12em;
      line-height: 1;
      text-transform: uppercase;
    }

    .ecosystem-hero h1 {
      max-inline-size: 13ch;
      font-size: clamp(2.45rem, 1.7rem + 3.75cqi, 5.7rem);
      letter-spacing: -0.065em;
      line-height: 0.94;
    }

    .ecosystem-hero__lead {
      max-inline-size: 45rem;
      color: var(--eco-muted);
      font-size: clamp(1rem, 0.92rem + 0.4cqi, 1.25rem);
      line-height: 1.82;
    }

    .ecosystem-hero__actions {
      display: flex;
      flex-wrap: wrap;
      gap: clamp(0.65rem, 0.5rem + 0.75cqi, 1rem);
      margin-block-start: clamp(0.3rem, 0.16rem + 0.7cqi, 0.8rem);
    }

    .ecosystem-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-block-size: clamp(2.65rem, 2.35rem + 1.5cqi, 3.25rem);
      border-radius: 100vi;
      padding-block: 0.35rem;
      padding-inline: clamp(0.95rem, 0.75rem + 1cqi, 1.45rem);
      font-weight: 850;
      line-height: 1;
      transition: border-color 180ms ease, background 180ms ease, box-shadow 180ms ease, translate 180ms ease;
    }

    .ecosystem-button--primary {
      background: linear-gradient(135deg, var(--eco-green), oklch(from var(--eco-green) calc(l - 9%) c calc(h + 8deg)));
      color: oklch(99% 0.01 180deg) !important;
      box-shadow: 0 1rem 2.4rem oklch(55% 0.16 174deg / 28%);
    }

    .ecosystem-button--ghost {
      border: 1px solid color-mix(in oklch, var(--eco-line), transparent 20%);
      background: color-mix(in oklch, var(--eco-panel), transparent 18%);
      color: var(--eco-ink) !important;
    }

    .ecosystem-button:hover,
    .ecosystem-card:hover {
      translate: 0 -0.22rem;
    }

    .ecosystem-orbit {
      position: relative;
      display: grid;
      min-block-size: clamp(17rem, 12rem + 25cqi, 27rem);
      overflow: clip;
      border: 1px solid color-mix(in oklch, var(--eco-line), transparent 34%);
      border-radius: var(--eco-radius);
      background:
        linear-gradient(90deg, color-mix(in oklch, var(--eco-line), transparent 88%) 1px, transparent 1px),
        linear-gradient(0deg, color-mix(in oklch, var(--eco-line), transparent 88%) 1px, transparent 1px),
        radial-gradient(circle, color-mix(in oklch, var(--eco-blue), transparent 86%), transparent 58%),
        var(--eco-panel-hard);
      background-size: clamp(1.2rem, 1rem + 1cqi, 1.8rem) clamp(1.2rem, 1rem + 1cqi, 1.8rem);
      place-items: center;
    }

    .ecosystem-orbit__ring {
      position: absolute;
      inset: 50% auto auto 50%;
      inline-size: min(76%, 22rem);
      aspect-ratio: 1;
      border: 1px dashed color-mix(in oklch, var(--eco-green), transparent 48%);
      border-radius: 50%;
      translate: -50% -50%;
    }

    .ecosystem-orbit__ring::after {
      position: absolute;
      inset: 18%;
      border: 1px dashed color-mix(in oklch, var(--eco-blue), transparent 58%);
      border-radius: inherit;
      content: "";
    }

    .ecosystem-orbit__core,
    .ecosystem-orbit__node {
      position: absolute;
      display: inline-grid;
      align-items: center;
      justify-items: center;
      border-radius: 100vi;
      font-weight: 900;
      box-shadow: 0 1rem 2.5rem oklch(16% 0.04 246deg / 18%);
    }

    .ecosystem-orbit__core {
      inline-size: clamp(5.5rem, 4.5rem + 5cqi, 8rem);
      aspect-ratio: 1;
      background: light-dark(oklch(99% 0.012 180deg / 88%), oklch(21% 0.03 246deg / 86%));
      color: var(--eco-ink);
      box-shadow: inset 0 0 0 1px color-mix(in oklch, var(--eco-line), transparent 28%), 0 1.35rem 3rem oklch(16% 0.04 246deg / 18%);
    }

    .ecosystem-orbit__node {
      padding-block: clamp(0.45rem, 0.36rem + 0.45cqi, 0.68rem);
      padding-inline: clamp(0.7rem, 0.55rem + 0.75cqi, 1.05rem);
      color: oklch(99% 0.01 180deg);
      font-size: clamp(0.78rem, 0.72rem + 0.3cqi, 0.95rem);
    }

    .ecosystem-orbit__node--android {
      inset-block-start: 14%;
      inset-inline-start: 10%;
      background: var(--eco-green);
    }

    .ecosystem-orbit__node--ios {
      inset-block-start: 18%;
      inset-inline-end: 8%;
      background: var(--eco-blue);
    }

    .ecosystem-orbit__node--windows {
      inset-block-end: 14%;
      inset-inline-end: 18%;
      background: var(--eco-gold);
      color: oklch(21% 0.045 78deg);
    }

    .ecosystem-metrics {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: var(--eco-gap);
    }

    .ecosystem-metric,
    .ecosystem-card,
    .ecosystem-principles {
      border: 1px solid color-mix(in oklch, var(--eco-line), transparent 24%);
      background: var(--eco-panel);
      box-shadow: 0 1rem 3rem oklch(16% 0.04 246deg / 9%);
    }

    .ecosystem-metric {
      display: grid;
      gap: 0.45rem;
      border-radius: calc(var(--eco-radius) * 0.72);
      padding-block: clamp(1rem, 0.8rem + 1cqi, 1.5rem);
      padding-inline: clamp(1rem, 0.8rem + 1cqi, 1.5rem);
    }

    .ecosystem-metric strong {
      font-size: clamp(1.45rem, 1.15rem + 1.5cqi, 2.35rem);
      letter-spacing: -0.04em;
      line-height: 1;
    }

    .ecosystem-metric span {
      color: var(--eco-muted);
      font-size: clamp(0.82rem, 0.78rem + 0.2cqi, 0.96rem);
    }

    .ecosystem-cards {
      display: grid;
      gap: var(--eco-gap);
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .ecosystem-card {
      --card-accent: var(--eco-green);
      position: relative;
      display: grid;
      grid-template-rows: auto auto auto 1fr auto;
      min-block-size: clamp(17rem, 14rem + 15cqi, 24rem);
      overflow: clip;
      border-color: color-mix(in oklch, var(--card-accent), var(--eco-line) 45%);
      border-radius: var(--eco-radius);
      padding: clamp(1.05rem, 0.8rem + 1.25cqi, 1.65rem);
      background:
        radial-gradient(circle at 82% 8%, color-mix(in oklch, var(--card-accent), transparent 72%), transparent 34%),
        linear-gradient(155deg, var(--eco-panel), color-mix(in oklch, var(--eco-bg), transparent 18%));
      transition: border-color 180ms ease, box-shadow 180ms ease, translate 180ms ease;
    }

    .ecosystem-card::before {
      position: absolute;
      inset-block: 0;
      inset-inline-start: 0;
      inline-size: clamp(0.28rem, 0.2rem + 0.4cqi, 0.45rem);
      background: var(--card-accent);
      content: "";
    }

    .ecosystem-card:hover {
      border-color: color-mix(in oklch, var(--card-accent), var(--eco-line) 12%);
      box-shadow: 0 1.5rem 4rem color-mix(in oklch, var(--card-accent), transparent 82%);
    }

    .ecosystem-card__kicker,
    .ecosystem-card__arrow {
      inline-size: fit-content;
    }

    .ecosystem-card__kicker {
      margin-block-start: clamp(1.25rem, 0.95rem + 1.5cqi, 2rem);
      color: color-mix(in oklch, var(--card-accent), var(--eco-ink) 24%);
      font-size: clamp(0.86rem, 0.8rem + 0.3cqi, 1rem);
      font-weight: 850;
    }

    .ecosystem-card strong {
      margin-block-start: clamp(0.3rem, 0.22rem + 0.4cqi, 0.55rem);
      font-size: clamp(1.25rem, 1.05rem + 1cqi, 1.8rem);
      letter-spacing: -0.035em;
      line-height: 1.08;
      text-wrap: balance;
    }

    .ecosystem-card p {
      margin-block-start: clamp(0.8rem, 0.65rem + 0.75cqi, 1.15rem);
      color: var(--eco-muted);
      font-size: clamp(0.95rem, 0.9rem + 0.25cqi, 1.05rem);
      line-height: 1.72;
    }

    .ecosystem-card__arrow {
      align-self: end;
      margin-block-start: clamp(1rem, 0.8rem + 1cqi, 1.4rem);
      color: color-mix(in oklch, var(--card-accent), var(--eco-ink) 18%);
      font-weight: 900;
    }

    .ecosystem-principles {
      display: grid;
      gap: var(--eco-gap);
      border-radius: var(--eco-radius);
      padding: var(--eco-pad);
    }

    .ecosystem-principles h2 {
      border: 0;
      padding: 0;
      font-size: clamp(1.65rem, 1.25rem + 2cqi, 3rem);
      letter-spacing: -0.045em;
      line-height: 1;
    }

    .ecosystem-principles__grid {
      display: grid;
      gap: var(--eco-gap);
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .ecosystem-principles article {
      display: grid;
      align-content: start;
      border: 1px solid color-mix(in oklch, var(--eco-line), transparent 36%);
      border-radius: calc(var(--eco-radius) * 0.7);
      padding: clamp(0.95rem, 0.72rem + 1.15cqi, 1.45rem);
      background: color-mix(in oklch, var(--eco-panel-hard), transparent 34%);
    }

    .ecosystem-principles h3 {
      font-size: clamp(1rem, 0.92rem + 0.4cqi, 1.2rem);
      line-height: 1.2;
    }

    .ecosystem-principles p {
      margin-block-start: 0.35rem;
      color: var(--eco-muted);
      line-height: 1.7;
    }

    @container ecosystem (inline-size > 54rem) {
      .ecosystem-hero {
        grid-template-columns: minmax(0, 1.12fr) minmax(18rem, 0.88fr);
      }
    }

    @container ecosystem (36rem < inline-size <= 54rem) {
      .ecosystem-cards,
      .ecosystem-principles__grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .ecosystem-card:first-child,
      .ecosystem-principles article:first-child {
        grid-column: 1 / -1;
      }
    }

    @container ecosystem (inline-size <= 36rem) {
      :scope {
        --eco-pad: clamp(1rem, 0.72rem + 1.4cqi, 1.35rem);
      }

      .ecosystem-cards,
      .ecosystem-metrics,
      .ecosystem-principles__grid {
        grid-template-columns: 1fr;
      }

      .ecosystem-orbit {
        min-block-size: clamp(15rem, 13rem + 10cqi, 18rem);
      }

      .ecosystem-actions {
        align-items: stretch;
        flex-direction: column;
      }
    }

    @media (prefers-reduced-motion: no-preference) {
      .ecosystem-orbit__ring {
        animation: ecosystem-sweep 18s linear infinite;
      }

      .ecosystem-orbit__node {
        animation: ecosystem-float 5s ease-in-out infinite alternate;
      }

      .ecosystem-orbit__node--ios {
        animation-delay: 700ms;
      }

      .ecosystem-orbit__node--windows {
        animation-delay: 1200ms;
      }
    }
  }

  @keyframes ecosystem-sweep {
    to {
      rotate: 1turn;
    }
  }

  @keyframes ecosystem-float {
    to {
      translate: 0 -0.45rem;
    }
  }
}
</style>

<style>
.ecosystem-landing {
  --ecosystem-accent: var(--vp-c-brand-1);
  --ecosystem-border: var(--vp-c-divider);
  --ecosystem-muted: var(--vp-c-text-2);
  --ecosystem-radius: 8px;
  display: grid;
  gap: 40px;
  max-width: 960px;
  margin: 16px auto 72px;
  color: var(--vp-c-text-1);
}

.ecosystem-landing::before,
.ecosystem-orbit {
  display: none;
}

.ecosystem-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  max-width: 720px;
  align-items: start;
  overflow: visible;
  padding: 12px 0 28px;
  border: 0;
  border-bottom: 1px solid var(--ecosystem-border);
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.ecosystem-hero__eyebrow {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--ecosystem-accent);
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.4;
  text-transform: none;
}

.ecosystem-hero h1 {
  max-width: 18ch;
  font-size: clamp(2.15rem, 4vw, 3.35rem);
  letter-spacing: 0;
  line-height: 1.12;
}

.ecosystem-hero__lead {
  max-width: 640px;
  color: var(--ecosystem-muted);
  font-size: 1.08rem;
  line-height: 1.75;
}

.ecosystem-hero__actions {
  gap: 10px;
  margin-top: 6px;
}

.ecosystem-button {
  min-height: 40px;
  border-radius: var(--ecosystem-radius);
  padding: 0 14px;
  font-weight: 600;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.ecosystem-button--primary {
  background: var(--vp-c-brand-3);
  box-shadow: none;
}

.ecosystem-button--ghost {
  border-color: var(--ecosystem-border);
  background: transparent;
}

.ecosystem-button:hover,
.ecosystem-card:hover {
  translate: none;
}

.ecosystem-metrics {
  gap: 0;
  border-block: 1px solid var(--ecosystem-border);
}

.ecosystem-metric {
  gap: 6px;
  border: 0;
  border-radius: 0;
  padding: 16px 20px;
  background: transparent;
  box-shadow: none;
}

.ecosystem-metric + .ecosystem-metric {
  border-inline-start: 1px solid var(--ecosystem-border);
}

.ecosystem-metric strong {
  font-size: 1.35rem;
  letter-spacing: 0;
}

.ecosystem-metric span {
  color: var(--ecosystem-muted);
  font-size: 0.82rem;
}

.ecosystem-cards {
  gap: 0;
  border: 1px solid var(--ecosystem-border);
  border-radius: var(--ecosystem-radius);
  overflow: hidden;
}

.ecosystem-card {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  min-height: 260px;
  padding: 24px;
  border: 0;
  border-radius: 0;
  border-top: 3px solid var(--card-accent);
  background: transparent;
  box-shadow: none;
}

.ecosystem-card + .ecosystem-card {
  border-inline-start: 1px solid var(--ecosystem-border);
}

.ecosystem-card::before {
  display: none;
}

.ecosystem-card:hover {
  border-color: var(--card-accent);
  background: var(--vp-c-bg-soft);
  box-shadow: none;
}

.ecosystem-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ecosystem-card__kicker {
  margin: 0;
  padding: 0;
  border-radius: 0;
  background: transparent;
  font-size: 0.78rem;
  line-height: 1.4;
}

.ecosystem-card__kicker {
  color: var(--ecosystem-muted);
  font-weight: 600;
}

.ecosystem-card h2 {
  margin: 22px 0 0;
  border: 0;
  padding: 0;
  color: var(--vp-c-text-1);
  font-size: 1.3rem;
  letter-spacing: 0;
  line-height: 1.25;
}

.ecosystem-card p {
  margin-top: 10px;
  color: var(--ecosystem-muted);
  font-size: 0.95rem;
  line-height: 1.7;
}

.ecosystem-card__arrow {
  margin-top: 24px;
  color: var(--vp-c-brand-1);
  font-size: 0.9rem;
  font-weight: 600;
}

.ecosystem-principles {
  gap: 24px;
  border: 0;
  border-radius: 0;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.ecosystem-principles h2 {
  font-size: 1.55rem;
  letter-spacing: 0;
  line-height: 1.25;
}

.ecosystem-principles__grid {
  gap: 0;
  border-top: 1px solid var(--ecosystem-border);
}

.ecosystem-principles article {
  padding: 20px 20px 0 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.ecosystem-principles article + article {
  padding-left: 20px;
  border-inline-start: 1px solid var(--ecosystem-border);
}

.ecosystem-principles h3 {
  font-size: 1rem;
  letter-spacing: 0;
}

.ecosystem-principles p {
  color: var(--ecosystem-muted);
  font-size: 0.92rem;
  line-height: 1.65;
}

@media (max-width: 640px) {
  .ecosystem-landing {
    gap: 28px;
    margin-top: 0;
  }

  .ecosystem-hero {
    padding-bottom: 24px;
  }

  .ecosystem-hero__actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .ecosystem-metrics,
  .ecosystem-cards,
  .ecosystem-principles__grid {
    grid-template-columns: 1fr;
  }

  .ecosystem-metric + .ecosystem-metric {
    border-top: 1px solid var(--ecosystem-border);
    border-inline-start: 0;
  }

  .ecosystem-card,
  .ecosystem-card + .ecosystem-card {
    min-height: 0;
    border-inline-start: 0;
    border-top: 3px solid var(--card-accent);
  }

  .ecosystem-card:first-child {
    border-top: 3px solid var(--card-accent);
  }

  .ecosystem-principles article,
  .ecosystem-principles article + article {
    padding: 18px 0 0;
    border-inline-start: 0;
  }

  .ecosystem-principles article + article {
    border-top: 1px solid var(--ecosystem-border);
  }
}
</style>




