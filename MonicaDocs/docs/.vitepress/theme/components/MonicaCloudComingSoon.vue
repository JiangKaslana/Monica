<script setup lang="ts">
import { computed } from "vue";
import { useData, useRouter, withBase } from "vitepress";

const router = useRouter();
const { lang } = useData();
const ns = "cloud-coming-soon";

const copy = {
  zh: {
    title: "正在火速开发中",
    description: "更完善的安全服务体验，即将抵达。",
    home: "返回首页",
    homePath: "/",
  },
  en: {
    title: "Under rapid development",
    description: "A more complete secure service experience is on its way.",
    home: "Back to home",
    homePath: "/en/",
  },
  ja: {
    title: "ただいま鋭意開発中です",
    description: "より充実した安全なサービス体験をまもなくお届けします。",
    home: "ホームへ戻る",
    homePath: "/ja/",
  },
  vi: {
    title: "Đang được phát triển khẩn trương",
    description: "Trải nghiệm dịch vụ bảo mật hoàn thiện hơn sắp ra mắt.",
    home: "Về trang chủ",
    homePath: "/vi/",
  },
  ru: {
    title: "Активно разрабатывается",
    description: "Более совершенный опыт безопасного сервиса уже на подходе.",
    home: "На главную",
    homePath: "/ru/",
  },
};

const currentCopy = computed(() => copy[lang.value.split("-")[0] as keyof typeof copy] ?? copy.zh);

const handleGoHome = () => {
  router.go(withBase(currentCopy.value.homePath));
};
</script>

<template>
  <main :class="[ns, 'flx-center']" aria-labelledby="cloud-coming-soon-title">
    <div :class="`${ns}__avatar-wrapper`">
      <img :src="withBase('/themepng.png')" :class="`${ns}__img`" alt="Monica-Cloud" />
    </div>

    <div :class="[`${ns}__detail`, 'flx-column']">
      <h1 id="cloud-coming-soon-title" class="glitch-text">Monica-Cloud</h1>
      <h4>{{ currentCopy.title }}</h4>
      <p class="cloud-tip">{{ currentCopy.description }}</p>

      <button class="acgn-btn" type="button" @click="handleGoHome">
        <span>{{ currentCopy.home }}</span>
      </button>
    </div>
  </main>
</template>

<style scoped lang="scss">
$namespace: cloud-coming-soon;

.#{$namespace} {
  display: flex;
  width: 100%;
  min-height: calc(100vh - var(--vp-nav-height));
  padding: 48px 24px;
  align-items: center;
  justify-content: center;
  gap: 80px;
  animation: cloudFadeIn 0.6s cubic-bezier(0.25, 1, 0.5, 1) both;

  &__avatar-wrapper {
    position: relative;
    animation: floating 3.5s ease-in-out infinite;
  }

  &__img {
    width: min(380px, 34vw);
    height: auto;
    object-fit: contain;
    filter: drop-shadow(0 8px 24px rgba(84, 211, 194, 0.15));
  }

  &__detail {
    display: flex;
    flex-direction: column;
    justify-content: center;

    h1 {
      margin: 0;
      background: linear-gradient(135deg, #54d3c2 0%, #a1c4fd 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      font-family: "Arial Rounded MT Bold", "Segoe UI Rounded", "Nunito", sans-serif;
      font-size: 58px;
      font-weight: 700;
      line-height: 1.05;
    }

    h4 {
      margin: 16px 0 6px;
      color: var(--vp-c-text-1);
      font-size: 20px;
      font-weight: 600;
    }

    .cloud-tip {
      margin: 0 0 28px;
      color: var(--vp-c-text-2);
      font-size: 14px;
      font-style: italic;
    }

    .acgn-btn {
      position: relative;
      width: 130px;
      height: 38px;
      overflow: hidden;
      border: 0;
      border-radius: 4px;
      background: #54d3c2;
      box-shadow: 0 4px 12px rgba(84, 211, 194, 0.3);
      color: #171a21;
      cursor: pointer;
      font-size: 14px;
      font-weight: 600;
      transition: all 0.3s ease;

      &::before {
        position: absolute;
        top: 0;
        left: -100%;
        width: 100%;
        height: 100%;
        background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
        content: "";
        transition: all 0.5s ease;
      }

      &:hover {
        background: #6fe0d0;
        box-shadow: 0 6px 18px rgba(84, 211, 194, 0.4);
        transform: translateY(-2px);

        &::before {
          left: 100%;
        }
      }

      &:focus-visible {
        outline: 2px solid var(--vp-c-brand-1);
        outline-offset: 4px;
      }

      &:active {
        transform: translateY(0);
      }
    }
  }

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 40px;
    padding: 40px 24px;
    text-align: center;

    &__img {
      width: min(260px, 70vw);
    }

    &__detail {
      align-items: center;

      h1 {
        font-size: 42px;
      }

      h4 {
        font-size: 18px;
      }
    }
  }
}

@keyframes floating {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-12px);
  }
}

@keyframes cloudFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
