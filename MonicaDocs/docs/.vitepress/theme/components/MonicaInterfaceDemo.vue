<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

// ============================================================
// Types
// ============================================================
interface PasswordEntry {
  id: number;
  title: string;
  username: string;
  password: string;
  url: string;
  notes: string;
  totpSecret: string;
  category: string;
  favorite: boolean;
  brandColor: string;
  groupId?: number;
}

interface PasswordGroup {
  id: number;
  name: string;
  icon: string;
}

interface OTPEntry {
  id: number;
  issuer: string;
  account: string;
  color: string;
  period: number;
}

interface CardEntry {
  id: number;
  type: "bank" | "document";
  bankName?: string;
  cardNumber?: string;
  holderName?: string;
  expiry?: string;
  docType?: string;
  docNumber?: string;
  color: string;
}

interface NoteEntry {
  id: number;
  title: string;
  preview: string;
  updatedAt: string;
}

// ============================================================
// Navigation state
// ============================================================
const activeTab = ref("passwords");
type VaultView = "list" | "detail" | "edit" | "add";
const vaultView = ref<VaultView>("list");
const selectedItem = ref<PasswordEntry | null>(null);
const selectedGroup = ref<PasswordGroup | null>(null);
const multiSelectMode = ref(false);
const selectedIds = ref<Set<number>>(new Set());

// ============================================================
// UI state
// ============================================================
const searchQuery = ref("");
const showSearch = ref(false);
const showMenu = ref(false);
const dark = ref(false);
const toast = ref("");
const copiedField = ref("");
let toastTimer: ReturnType<typeof setTimeout> | undefined;

// Generator state
const generatorMode = ref<"random" | "words" | "phrase" | "pin" | "ssh">("random");
const genLength = ref(14);
const genUppercase = ref(true);
const genLowercase = ref(true);
const genDigits = ref(true);
const genSymbols = ref(true);
const generatedPassword = ref("M7v!pQ9#Lx2@wR");
const genHistory = ref<string[]>(["nR8k!Tp2@Wx5#Qm", "Kp4$vL9^Jf3&aB6"]);

// Form state
const form = ref({ title: "", username: "", password: "", url: "", notes: "", totpSecret: "", category: "" });

// ============================================================
// Data
// ============================================================
const tabs = [
  { id: "passwords", label: "密码" },
  { id: "vault", label: "密码库" },
  { id: "authenticator", label: "验证器" },
  { id: "cards", label: "卡包" },
  { id: "notes", label: "笔记" },
  { id: "send", label: "发送" },
  { id: "steam", label: "Steam" },
  { id: "generator", label: "生成器" },
  { id: "settings", label: "设置" },
];

// SVG icons — 24x24 Material-style, stroke-based
const icons: Record<string, string> = {
  vault: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2"/><path d="M3 13h18"/><circle cx="12" cy="16.5" r="1.5"/></svg>',
  passwords: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="12" r="5.5"/><path d="M21 2l-5 5M12.5 7.5L17 3"/></svg>',
  authenticator: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11.5 14.5 15 9"/></svg>',
  cards: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="15" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/><line x1="7" y1="15" x2="10" y2="15"/></svg>',
  notes: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="13 2 13 9 20 9"/><line x1="8 13" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="13" y2="17"/></svg>',
  send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>',
  steam: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8.5 8.5l7 7M9 14h.01"/><circle cx="12" cy="12" r="2.5"/></svg>',
  generator: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="16.5 3 21 3 21 7.5"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16.5 21 21 16.5 21"/><line x1="14" y1="14" x2="21" y2="21"/><line x1="3" y1="4" x2="10" y2="11"/></svg>',
  settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>',
  more: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>',
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>',
  dark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>',
  light: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/></svg>',
  star: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
  starFilled: '<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>',
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
  add: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
  chevronDown: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>',
  chevronUp: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15"/></svg>',
  edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>',
};

const groups = ref<PasswordGroup[]>([
  { id: 1, name: "Google 账号", icon: "G" },
  { id: 2, name: "工作账号", icon: "W" },
]);

const passwords = ref<PasswordEntry[]>([
  { id: 1, title: "GitHub", username: "monica@example.com", password: "nR8k!Tp2@Wx5#Qm", url: "github.com", notes: "主账号，已开启2FA", totpSecret: "JBSWY3DPEHPK3PXP", category: "开发", favorite: true, brandColor: "#171b24" },
  { id: 2, title: "Gmail", username: "me@gmail.com", password: "fK2#vL9^Jf3&aB6", url: "mail.google.com", notes: "", totpSecret: "", category: "个人", favorite: false, brandColor: "#ea4335", groupId: 1 },
  { id: 3, title: "YouTube", username: "monica_channel", password: "sP4*Wx8$Yq1@cD7", url: "youtube.com", notes: "创作账号", totpSecret: "", category: "个人", favorite: false, brandColor: "#ff0000", groupId: 1 },
  { id: 4, title: "Google Drive", username: "me@gmail.com", password: "fK2#vL9^Jf3&aB6", url: "drive.google.com", notes: "共享密码", totpSecret: "", category: "个人", favorite: false, brandColor: "#4285f4", groupId: 1 },
  { id: 5, title: "Notion", username: "hello@monica.app", password: "pX7$zM3@hT5&kL9", url: "notion.so", notes: "团队空间", totpSecret: "", category: "工作", favorite: true, brandColor: "#111111", groupId: 2 },
  { id: 6, title: "Slack", username: "monica@team.slack.com", password: "tR3#qH6!wE2@jN8", url: "slack.com", notes: "", totpSecret: "", category: "工作", favorite: false, brandColor: "#4a154b", groupId: 2 },
  { id: 7, title: "Steam", username: "monica_pass", password: "sP4*Wx8$Yq1@cD7", url: "store.steampowered.com", notes: "", totpSecret: "", category: "游戏", favorite: false, brandColor: "#1b4f78" },
  { id: 8, title: "AWS Console", username: "admin@monica", password: "cM9&vB3#xR7!wF5", url: "aws.amazon.com", notes: "IAM 用户，MFA 已开启", totpSecret: "JBSWY3DPEHPK3PXP", category: "开发", favorite: false, brandColor: "#ff9900" },
]);

const otpEntries = ref<OTPEntry[]>([
  { id: 1, issuer: "Monica Account", account: "monica@example.com", color: "#006b62", period: 30 },
  { id: 2, issuer: "Google", account: "me@gmail.com", color: "#4285f4", period: 30 },
  { id: 3, issuer: "GitHub", account: "monica", color: "#171b24", period: 30 },
]);

const cardEntries = ref<CardEntry[]>([
  { id: 1, type: "bank", bankName: "Monica Bank", cardNumber: "•••• •••• •••• 4728", holderName: "MONICA PASS", expiry: "12/28", color: "#147b70" },
  { id: 2, type: "bank", bankName: "招商银行", cardNumber: "•••• •••• •••• 8892", holderName: "ZHANG SAN", expiry: "06/27", color: "#c0392b" },
  { id: 3, type: "document", docType: "身份证", docNumber: "3***********8", color: "#4f8cff" },
]);

const noteEntries = ref<NoteEntry[]>([
  { id: 1, title: "GitHub 恢复码", preview: "已加密 · 包含 16 组恢复码", updatedAt: "10 分钟前" },
  { id: 2, title: "设备信息备忘", preview: "已加密 · Pixel 8 Pro IMEI", updatedAt: "昨天" },
  { id: 3, title: "服务器 SSH 密钥", preview: "已加密 · 4 台服务器", updatedAt: "3 天前" },
]);

const sendItems = ref([{ id: 1, name: "配置文件分享", type: "文件", expiry: "24小时后过期", recipient: "" }]);
const steamInfo = ref({ steamId: "STEAM_0:1:12345678", level: 42, vacBanned: false, gameCount: 156 });

// ============================================================
// Computed
// ============================================================
const tabLabel = computed(() => tabs.find((t) => t.id === activeTab.value)?.label ?? "Monica");
const isPasswordTab = computed(() => activeTab.value === "passwords" || activeTab.value === "vault");

const groupedPasswords = computed(() => {
  const singles = passwords.value.filter((p) => !p.groupId);
  const grouped = new Map<number, { group: PasswordGroup; entries: PasswordEntry[] }>();
  passwords.value.filter((p) => p.groupId).forEach((p) => {
    const g = groups.value.find((g) => g.id === p.groupId);
    if (!g) return;
    if (!grouped.has(p.groupId!)) grouped.set(p.groupId!, { group: g, entries: [] });
    grouped.get(p.groupId!)!.entries.push(p);
  });
  return { singles, grouped: [...grouped.values()] };
});

const filteredPasswords = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return passwords.value;
  return passwords.value.filter((p) => `${p.title} ${p.username} ${p.url} ${p.category}`.toLowerCase().includes(q));
});

const filteredSingles = computed(() => filteredPasswords.value.filter((p) => !p.groupId));
const filteredGroups = computed(() => {
  const gm = new Map<number, { group: PasswordGroup; entries: PasswordEntry[] }>();
  filteredPasswords.value.filter((p) => p.groupId).forEach((p) => {
    const g = groups.value.find((grp) => grp.id === p.groupId);
    if (!g) return;
    if (!gm.has(p.groupId!)) gm.set(p.groupId!, { group: g, entries: [] });
    gm.get(p.groupId!)!.entries.push(p);
  });
  return [...gm.values()];
});

const currentGroupEntries = computed(() => {
  if (!selectedGroup.value) return [];
  return passwords.value.filter((p) => p.groupId === selectedGroup.value!.id);
});

const selectedCount = computed(() => selectedIds.value.size);

// ============================================================
// Toast
// ============================================================
const notify = (message: string) => {
  toast.value = message;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ""), 2200);
};

// ============================================================
// Navigation
// ============================================================
const switchTab = (id: string) => {
  activeTab.value = id;
  vaultView.value = "list";
  selectedItem.value = null;
  selectedGroup.value = null;
  multiSelectMode.value = false;
  selectedIds.value = new Set();
  showSearch.value = false;
  searchQuery.value = "";
  showMenu.value = false;
};

const openDetail = (item: PasswordEntry) => {
  if (multiSelectMode.value) { toggleSelect(item.id); return; }
  selectedItem.value = item;
  vaultView.value = "detail";
};

const openGroup = (group: PasswordGroup) => {
  selectedGroup.value = group;
};

const closeGroup = () => {
  selectedGroup.value = null;
};

const openAdd = () => {
  form.value = { title: "", username: "", password: genPassword(), url: "", notes: "", totpSecret: "", category: "" };
  vaultView.value = "add";
};

const openEdit = (item?: PasswordEntry) => {
  const target = item || selectedItem.value;
  if (!target) return;
  form.value = { title: target.title, username: target.username, password: target.password, url: target.url, notes: target.notes, totpSecret: target.totpSecret, category: target.category };
  vaultView.value = "edit";
};

const backToList = () => {
  vaultView.value = "list";
  selectedItem.value = null;
  selectedGroup.value = null;
  multiSelectMode.value = false;
  selectedIds.value = new Set();
};

// ============================================================
// Password CRUD
// ============================================================
const genPassword = () => {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
  return Array.from({ length: genLength.value }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
};

const savePassword = () => {
  if (!form.value.title.trim() || !form.value.username.trim()) return;
  const isEdit = vaultView.value === "edit" && selectedItem.value;
  if (isEdit) {
    Object.assign(selectedItem.value!, form.value);
    notify("密码已更新");
  } else {
    passwords.value.unshift({
      id: Date.now(), ...form.value,
      favorite: false,
      brandColor: ["#7357c9", "#e6764f", "#217a74", "#4285f4", "#ff9900"][passwords.value.length % 5],
    });
    notify("已添加到密码库");
  }
  backToList();
};

const removePassword = (id: number) => {
  if (selectedItem.value?.id === id) selectedItem.value = null;
  passwords.value = passwords.value.filter((p) => p.id !== id);
  selectedIds.value.delete(id);
  notify("已移至回收站");
};

const toggleFavorite = (item: PasswordEntry) => {
  item.favorite = !item.favorite;
  notify(item.favorite ? "已加入收藏" : "已取消收藏");
};

// ============================================================
// Multi-select
// ============================================================
const toggleSelect = (id: number) => {
  const next = new Set(selectedIds.value);
  next.has(id) ? next.delete(id) : next.add(id);
  selectedIds.value = next;
};

const enterMultiSelect = (item: PasswordEntry) => {
  multiSelectMode.value = true;
  selectedIds.value = new Set([item.id]);
};

const selectAll = () => {
  const allIds = filteredPasswords.value.map((p) => p.id);
  selectedIds.value = selectedCount.value === allIds.length ? new Set() : new Set(allIds);
};

const batchDelete = () => {
  const ids = [...selectedIds.value];
  passwords.value = passwords.value.filter((p) => !ids.includes(p.id));
  notify(`已删除 ${ids.length} 项`);
  multiSelectMode.value = false;
  selectedIds.value = new Set();
};

const batchFavorite = () => {
  [...selectedIds.value].forEach((id) => {
    const p = passwords.value.find((p) => p.id === id);
    if (p) p.favorite = true;
  });
  notify(`已收藏 ${selectedIds.value.size} 项`);
  multiSelectMode.value = false;
  selectedIds.value = new Set();
};

// ============================================================
// Copy
// ============================================================
const copyText = (text: string, label: string) => {
  navigator.clipboard?.writeText(text);
  copiedField.value = label;
  notify(`${label}已复制`);
  setTimeout(() => (copiedField.value = ""), 1500);
};

const copyOtpCode = (code: string) => {
  navigator.clipboard?.writeText(code);
  notify("验证码已复制");
};

// ============================================================
// Generator
// ============================================================
const regenerate = () => {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
  generatedPassword.value = Array.from({ length: genLength.value }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
  genHistory.value.unshift(generatedPassword.value);
  if (genHistory.value.length > 5) genHistory.value.pop();
  notify("已生成新密码");
};

// ============================================================
// M3E Color scheme presets
// ============================================================
const colorSchemes = [
  { id: "default", label: "默认", primary: "#006b62" },
  { id: "ocean", label: "海洋蓝", primary: "#1565C0" },
  { id: "sunset", label: "日落橙", primary: "#FF7043" },
  { id: "forest", label: "森林绿", primary: "#2E7D32" },
  { id: "tech", label: "科技紫", primary: "#7B1FA2" },
  { id: "blackmamba", label: "黑曼巴", primary: "#552583" },
  { id: "greystyle", label: "小黑紫", primary: "#616161" },
  { id: "waterlilies", label: "睡莲", primary: "#00796B" },
];

const activeColorScheme = ref("default");

const applyColorScheme = (id: string) => {
  activeColorScheme.value = id;
};

// ============================================================
// Sidebar/nav state
// ============================================================
const sidebarCollapsed = ref(false);

// ============================================================
// TOTP live timer
// ============================================================
const tick = ref(0);
let tickTimer: ReturnType<typeof setInterval> | undefined;

const totpSeed = (id: number) => id * 137 + 420;

const getOtpCode = (seed: number, period: number) => {
  void tick.value; // reactively depend on tick
  const win = Math.floor(Date.now() / 1000 / period);
  let hash = (seed * 31 + win * 7 + (win >> 3) * 13) & 0x7fffffff;
  return String(hash % 1000000).padStart(6, "0");
};

const getOtpElapsed = (period: number) => {
  void tick.value;
  return Math.floor(Date.now() / 1000) % period;
};
const getOtpProgress = (period: number) => (getOtpElapsed(period) / period) * 100;

// ============================================================
// Lifecycle
// ============================================================
onBeforeUnmount(() => {
  toastTimer && clearTimeout(toastTimer);
  tickTimer && clearInterval(tickTimer);
});
onMounted(() => {
  tickTimer = setInterval(() => tick.value++, 1000);
});
</script>

<template>
  <section
    class="monica-demo"
    :class="{ 'is-dark': dark }"
    aria-label="Monica 交互演示"
  >
    <div class="app-surface">
      <!-- Sidebar Nav -->
      <aside class="app-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-brand">
          <span class="brand-mark"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg></span>
          <strong>Monica</strong>
        </div>
        <p class="sidebar-section">个人空间</p>
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="nav-item"
          :class="{ active: activeTab === tab.id }"
          @click="switchTab(tab.id)"
        >
          <span class="nav-icon" v-html="icons[tab.id]"></span>
          <small>{{ tab.label }}</small>
        </button>
        <div class="sidebar-footer">
          <button type="button" class="nav-item" @click="sidebarCollapsed = !sidebarCollapsed">
            <span class="nav-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline v-if="sidebarCollapsed" points="13 17 18 12 13 7"/><polyline v-else points="11 17 6 12 11 7"/></svg></span>
            <small>收起菜单</small>
          </button>
        </div>
      </aside>

      <!-- Main Content -->
      <div class="app-main">
        <!-- Header -->
        <header class="app-header">
          <div class="header-left">
            <button v-if="isPasswordTab && vaultView !== 'list'" type="button" class="back-btn" @click="backToList" v-html="icons.back"></button>
            <div>
              <p>Monica</p>
              <h3 v-if="!isPasswordTab">{{ tabLabel }}</h3>
              <h3 v-else-if="vaultView === 'list'">密码库</h3>
              <h3 v-else-if="vaultView === 'detail'">密码详情</h3>
              <h3 v-else-if="vaultView === 'edit'">编辑密码</h3>
              <h3 v-else-if="vaultView === 'add'">新建密码</h3>
            </div>
          </div>
          <div class="header-actions">
            <button class="icon-button" type="button" aria-label="切换明暗模式" @click="dark = !dark" v-html="dark ? icons.light : icons.dark"></button>
            <button v-if="isPasswordTab && vaultView === 'list'" class="icon-button" type="button" aria-label="搜索" @click="showSearch = !showSearch" v-html="icons.search"></button>
            <button class="icon-button" type="button" aria-label="更多选项" @click="showMenu = !showMenu" v-html="icons.more"></button>
          </div>
          <div v-if="showMenu" class="quick-menu" @mouseleave="showMenu = false">
            <button v-if="isPasswordTab && vaultView === 'list'" type="button" @click="showSearch = !showSearch; showMenu = false">{{ showSearch ? '关闭搜索' : '搜索密码库' }}</button>
            <button type="button" @click="switchTab('settings'); showMenu = false">外观设置</button>
            <button type="button" @click="switchTab('generator'); showMenu = false">密码生成器</button>
          </div>
        </header>

        <!-- Search bar -->
        <div v-if="showSearch && isPasswordTab && vaultView === 'list'" class="search-bar">
          <span class="search-icon" v-html="icons.search"></span>
          <input v-model="searchQuery" type="search" placeholder="搜索密码库（标题、账号、网址）" autofocus />
          <button v-if="searchQuery" class="search-clear" type="button" aria-label="清除搜索" @click="searchQuery = ''" v-html="icons.close"></button>
        </div>

        <!-- ============================================================ -->
        <!-- CONTENT: Password / Vault Tab -->
        <!-- ============================================================ -->
        <main class="app-content" v-if="isPasswordTab">
          <!-- LIST VIEW -->
          <template v-if="vaultView === 'list'">
            <!-- Summary card -->
            <div class="summary-card">
              <div class="summary-info">
                <span class="summary-icon" v-html="icons.vault"></span>
                <strong>{{ passwords.length }}</strong>
                <small>已保存项目</small>
              </div>
              <div class="summary-stats">
                <span>{{ passwords.filter(p => p.favorite).length }} 收藏</span>
                <span>{{ groups.length }} 分组</span>
              </div>
            </div>

            <!-- Section: Groups -->
            <template v-if="filteredGroups.length && !searchQuery">
              <div class="section-heading"><span>密码组</span><small>{{ filteredGroups.length }} 组</small></div>
              <div class="group-list">
                <article
                  v-for="g in filteredGroups"
                  :key="g.group.id"
                  class="group-card stacked"
                  :class="{ expanded: selectedGroup?.id === g.group.id }"
                  tabindex="0"
                >
                  <!-- Phantom layers -->
                  <div class="phantom p3"></div>
                  <div class="phantom p2"></div>
                  <div class="phantom p1"></div>
                  <!-- Main card -->
                  <div class="group-main" @click="selectedGroup?.id === g.group.id ? closeGroup() : openGroup(g.group)">
                    <span class="group-icon">{{ g.group.icon }}</span>
                    <div class="group-info">
                      <strong>{{ g.group.name }}</strong>
                      <small>{{ g.entries.length }} 个账号</small>
                    </div>
                    <span class="group-badge">{{ g.entries.length }}</span>
                    <span class="group-chevron" v-html="selectedGroup?.id === g.group.id ? icons.chevronUp : icons.chevronDown"></span>
                  </div>
                  <!-- Expanded inner -->
                  <div v-if="selectedGroup?.id === g.group.id" class="group-inner">
                    <div v-for="entry in currentGroupEntries" :key="entry.id" class="inner-item" tabindex="0" @click="openDetail(entry)">
                      <span class="brand-dot" :style="{ background: entry.brandColor }">{{ entry.title[0] }}</span>
                      <div class="inner-copy">
                        <strong>{{ entry.title }}</strong>
                        <span>{{ entry.username }}</span>
                      </div>
                      <button class="inner-star" type="button" :aria-label="entry.favorite ? '取消收藏' : '收藏'" @click.stop="toggleFavorite(entry)" v-html="entry.favorite ? icons.starFilled : icons.star"></button>
                    </div>
                  </div>
                </article>
              </div>
            </template>

            <!-- Section: All Passwords -->
            <div class="section-heading"><span>{{ searchQuery ? '搜索结果' : '最近使用' }}</span><small>{{ filteredSingles.length }} 项</small></div>
            <div class="credential-list">
              <article
                v-for="item in filteredSingles"
                :key="item.id"
                class="credential-card"
                :class="{ selected: selectedIds.has(item.id) }"
                tabindex="0"
                @click="openDetail(item)"
                @keydown.enter="openDetail(item)"
              >
                <!-- Swipe delete: left action -->
                <div class="swipe-bg"><span>删除</span></div>
                <div class="card-surface">
                  <span class="brand-icon" :style="{ background: item.brandColor }">{{ item.title[0] }}</span>
                  <div class="credential-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.username }}</span>
                    <small>{{ item.password.slice(0, 4) + '••••••••••' }}</small>
                  </div>
                  <div class="card-actions" @click.stop>
                    <button class="small-icon" type="button" :aria-label="item.favorite ? '取消收藏' : '收藏'" @click="toggleFavorite(item)" v-html="item.favorite ? icons.starFilled : icons.star"></button>
                    <button class="small-icon" type="button" aria-label="删除密码" @click="removePassword(item.id)" v-html="icons.trash"></button>
                  </div>
                </div>
              </article>
              <p v-if="!filteredSingles.length && !filteredGroups.length" class="empty-state">没有匹配的密码</p>
            </div>
          </template>

          <!-- DETAIL VIEW -->
          <template v-else-if="vaultView === 'detail' && selectedItem">
            <div class="detail-view">
              <div class="detail-hero">
                <span class="detail-brand" :style="{ background: selectedItem.brandColor }">{{ selectedItem.title[0] }}</span>
                <div>
                  <h2>{{ selectedItem.title }}</h2>
                  <p>{{ selectedItem.url }}</p>
                </div>
                <button class="detail-fav" type="button" @click="toggleFavorite(selectedItem)" v-html="selectedItem.favorite ? icons.starFilled : icons.star"></button>
              </div>
              <div class="detail-fields">
                <div class="detail-field" @click="copyText(selectedItem.username, '用户名')">
                  <span class="field-label">用户名</span>
                  <span class="field-value">{{ selectedItem.username }}</span>
                  <span class="field-copy" :class="{ copied: copiedField === '用户名' }" v-html="copiedField === '用户名' ? icons.check : icons.copy"></span>
                </div>
                <div class="detail-field" @click="copyText(selectedItem.password, '密码')">
                  <span class="field-label">密码</span>
                  <span class="field-value mono">{{ selectedItem.password }}</span>
                  <span class="field-copy" :class="{ copied: copiedField === '密码' }" v-html="copiedField === '密码' ? icons.check : icons.copy"></span>
                </div>
                <div v-if="selectedItem.url" class="detail-field" @click="copyText(selectedItem.url, '网址')">
                  <span class="field-label">网址</span>
                  <span class="field-value">{{ selectedItem.url }}</span>
                  <span class="field-copy" :class="{ copied: copiedField === '网址' }" v-html="copiedField === '网址' ? icons.check : icons.copy"></span>
                </div>
                <div v-if="selectedItem.totpSecret" class="detail-field totp-field">
                  <span class="field-label">两步验证 (TOTP)</span>
                  <span class="field-value mono otp-code">482 916</span>
                  <span class="field-timer">{{ 30 - (Math.floor(Date.now() / 1000) % 30) }}s</span>
                </div>
                <div v-if="selectedItem.notes" class="detail-field">
                  <span class="field-label">备注</span>
                  <span class="field-value">{{ selectedItem.notes }}</span>
                </div>
              </div>
              <div class="detail-actions">
                <button class="action-btn primary" type="button" @click="openEdit()"><span v-html="icons.edit"></span> 编辑</button>
                <button class="action-btn danger" type="button" @click="removePassword(selectedItem!.id); backToList()"><span v-html="icons.trash"></span> 删除</button>
              </div>
            </div>
          </template>

          <!-- ADD / EDIT VIEW -->
          <template v-else-if="vaultView === 'add' || vaultView === 'edit'">
            <form class="entry-form" @submit.prevent="savePassword">
              <div class="form-group">
                <label>名称 <span>*</span></label>
                <input v-model="form.title" required placeholder="例如 GitHub" />
              </div>
              <div class="form-group">
                <label>用户名 <span>*</span></label>
                <input v-model="form.username" required placeholder="name@example.com" />
              </div>
              <div class="form-group">
                <label>密码</label>
                <div class="password-row">
                  <input v-model="form.password" placeholder="输入或生成密码" />
                  <button type="button" class="gen-btn" @click="form.password = genPassword()" title="生成密码" v-html="icons.generator"></button>
                </div>
              </div>
              <div class="form-group">
                <label>网址</label>
                <input v-model="form.url" placeholder="example.com" />
              </div>
              <div class="form-group">
                <label>备注</label>
                <textarea v-model="form.notes" placeholder="附加信息..." rows="2"></textarea>
              </div>
              <div class="form-group">
                <label>TOTP 密钥</label>
                <input v-model="form.totpSecret" placeholder="手动输入或扫描二维码" />
              </div>
              <div class="form-actions">
                <button class="action-btn primary" type="submit">{{ vaultView === 'edit' ? '保存修改' : '保存项目' }}</button>
                <button class="action-btn ghost" type="button" @click="backToList">取消</button>
              </div>
              <p class="form-hint">💡 在设置中配置"常用账号信息"可自动填充</p>
            </form>
          </template>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Authenticator -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'authenticator'">
          <div class="auth-hero">
            <span>双重验证 (2FA)</span>
            <small>验证码在设备本地生成，安全可靠</small>
          </div>
          <div class="otp-list">
            <article
              v-for="otp in otpEntries" :key="otp.id"
              class="otp-card"
              :class="{ warning: getOtpElapsed(otp.period) >= otp.period - 5 }"
              @click="copyOtpCode(getOtpCode(totpSeed(otp.id), otp.period))"
            >
              <span class="otp-logo" :style="{ background: otp.color }">{{ otp.issuer[0] }}</span>
              <div class="otp-info">
                <strong>{{ otp.issuer }}</strong>
                <small>{{ otp.account }}</small>
              </div>
              <div class="otp-right">
                <strong class="otp-code">{{ getOtpCode(totpSeed(otp.id), otp.period).slice(0, 3) }} {{ getOtpCode(totpSeed(otp.id), otp.period).slice(3) }}</strong>
                <small class="otp-timer">{{ getOtpElapsed(otp.period) }}s</small>
                <div class="otp-progress"><i :style="{ width: getOtpProgress(otp.period) + '%' }"></i></div>
              </div>
            </article>
          </div>
          <button class="add-otp-btn" type="button" @click="notify('扫码添加验证器')">+ 添加验证器</button>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Cards -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'cards'">
          <div class="card-section">
            <div class="section-heading"><span>银行卡</span></div>
            <article v-for="card in cardEntries.filter(c => c.type === 'bank')" :key="card.id" class="bank-card" :style="{ background: `linear-gradient(135deg, ${card.color}, color-mix(in srgb, ${card.color} 70%, #0a1a1a))` }">
              <small>{{ card.bankName }}</small>
              <b>{{ card.cardNumber }}</b>
              <div class="bank-bottom"><span>{{ card.holderName }}</span><span>{{ card.expiry }}</span></div>
            </article>
          </div>
          <div class="card-section">
            <div class="section-heading"><span>证件</span></div>
            <article v-for="card in cardEntries.filter(c => c.type === 'document')" :key="card.id" class="doc-card">
              <span class="doc-icon">🪪</span>
              <div><strong>{{ card.docType }}</strong><small>{{ card.docNumber }}</small></div>
            </article>
          </div>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Notes -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'notes'">
          <article v-for="note in noteEntries" :key="note.id" class="note-card">
            <span class="note-accent">🔒</span>
            <div class="note-body">
              <strong>{{ note.title }}</strong>
              <small>{{ note.preview }}</small>
              <span class="note-time">{{ note.updatedAt }}</span>
            </div>
          </article>
          <button class="add-otp-btn" type="button" @click="notify('新建安全笔记')">+ 新建笔记</button>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Send -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'send'">
          <div class="auth-hero"><span>Bitwarden Send</span><small>安全地发送文件或文本</small></div>
          <article v-for="s in sendItems" :key="s.id" class="send-card">
            <span class="send-icon">↗</span>
            <div class="send-info"><strong>{{ s.name }}</strong><small>{{ s.type }} · {{ s.expiry }}</small></div>
            <span class="send-status">活跃</span>
          </article>
          <button class="add-otp-btn" type="button" @click="notify('新建发送')">+ 新建发送</button>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Steam -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'steam'">
          <div class="steam-card">
            <div class="steam-header">
              <span class="steam-avatar">S</span>
              <div><strong>Monica_Pass</strong><small>STEAM_0:1:12345678</small></div>
            </div>
            <div class="steam-stats">
              <div class="steam-stat"><b>{{ steamInfo.level }}</b><small>等级</small></div>
              <div class="steam-stat"><b>{{ steamInfo.gameCount }}</b><small>游戏</small></div>
              <div class="steam-stat"><b>{{ steamInfo.vacBanned ? '有' : '无' }}</b><small>VAC</small></div>
            </div>
          </div>
          <p class="page-note">Steam 账号信息 · 本地安全存储</p>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Generator -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'generator'">
          <div class="gen-tabs">
            <button v-for="m in (['random','words','phrase','pin','ssh'] as const)" :key="m" type="button" class="gen-tab" :class="{ active: generatorMode === m }" @click="generatorMode = m">{{ { random: '随机', words: '单词', phrase: '短语', pin: 'PIN', ssh: 'SSH' }[m] }}</button>
          </div>
          <div class="gen-output-card">
            <output class="gen-output">{{ generatedPassword }}</output>
            <button class="gen-copy" type="button" @click="copyText(generatedPassword, '密码'); notify('密码已复制')" v-html="icons.copy"></button>
          </div>
          <div class="gen-options">
            <div class="gen-option"><span>长度</span><b>{{ genLength }}</b><input type="range" min="4" max="64" v-model.number="genLength" /></div>
            <div class="gen-toggles">
              <button type="button" :class="{ on: genUppercase }" @click="genUppercase = !genUppercase">A-Z</button>
              <button type="button" :class="{ on: genLowercase }" @click="genLowercase = !genLowercase">a-z</button>
              <button type="button" :class="{ on: genDigits }" @click="genDigits = !genDigits">0-9</button>
              <button type="button" :class="{ on: genSymbols }" @click="genSymbols = !genSymbols">!@#</button>
            </div>
          </div>
          <button class="action-btn primary full" type="button" @click="regenerate">重新生成</button>
          <div class="gen-history" v-if="genHistory.length">
            <div class="section-heading"><span>历史记录</span></div>
            <div v-for="(h, i) in genHistory" :key="i" class="gen-hist-item" @click="copyText(h, '密码')">{{ h }}</div>
          </div>
        </main>

        <!-- ============================================================ -->
        <!-- CONTENT: Settings -->
        <!-- ============================================================ -->
        <main class="app-content" v-else-if="activeTab === 'settings'">
          <!-- Color scheme -->
          <div class="settings-group">
            <span>配色方案</span>
            <div class="color-grid">
              <button
                v-for="scheme in colorSchemes"
                :key="scheme.id"
                type="button"
                class="color-dot"
                :class="{ active: activeColorScheme === scheme.id }"
                :style="{ background: scheme.primary }"
                :aria-label="scheme.label"
                :title="scheme.label"
                @click="applyColorScheme(scheme.id)"
              >
                <span v-if="activeColorScheme === scheme.id">✓</span>
              </button>
            </div>
          </div>
          <!-- Appearance -->
          <div class="settings-group">
            <span>外观</span>
            <button type="button" @click="dark = !dark">{{ dark ? '深色模式' : '浅色模式' }}<b>{{ dark ? '开' : '关' }}</b></button>
            <button type="button">语言<b>简体中文</b></button>
          </div>
          <!-- Security -->
          <div class="settings-group">
            <span>安全</span>
            <button type="button">自动锁定<b>5 分钟</b></button>
            <button type="button">指纹解锁<b>已开启</b></button>
            <button type="button">防截屏保护<b>已开启</b></button>
          </div>
          <!-- Data -->
          <div class="settings-group">
            <span>数据</span>
            <button type="button">同步与备份<b>WebDAV</b></button>
            <button type="button">导出数据<b>未加密 JSON / KeePass</b></button>
            <button type="button">回收站<b>3 项</b></button>
          </div>
        </main>

        <!-- FAB -->
        <button v-if="isPasswordTab && vaultView === 'list'" class="fab" type="button" aria-label="新建密码" @click="openAdd">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          <span class="fab-label">新建密码</span>
        </button>

        <!-- Multi-select bottom bar -->
        <div v-if="multiSelectMode && isPasswordTab" class="multi-bar">
          <span class="multi-count">已选 {{ selectedCount }} 项</span>
          <button type="button" @click="selectAll">{{ selectedCount === filteredPasswords.length ? '取消全选' : '全选' }}</button>
          <button type="button" @click="batchFavorite">★ 收藏</button>
          <button type="button" class="danger" @click="batchDelete">⌫ 批量删除</button>
          <button type="button" @click="multiSelectMode = false; selectedIds = new Set()">✕ 取消</button>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <Transition name="toast"><p v-if="toast" class="toast" role="status">{{ toast }}</p></Transition>
  </section>
</template>

<style scoped>
/* ============================================================
   M3E Design Token System
   ============================================================ */
.monica-demo {
  --m3-primary: oklch(52% 0.12 178);
  --m3-on-primary: #ffffff;
  --m3-primary-container: oklch(88% 0.06 178);
  --m3-on-primary-container: oklch(24% 0.06 178);
  --m3-secondary: oklch(43% 0.05 178);
  --m3-on-secondary: #ffffff;
  --m3-secondary-container: oklch(86% 0.05 178);
  --m3-on-secondary-container: oklch(18% 0.05 178);
  --m3-tertiary: oklch(42% 0.08 220);
  --m3-on-tertiary: #ffffff;
  --m3-tertiary-container: oklch(88% 0.06 220);
  --m3-on-tertiary-container: oklch(18% 0.06 220);
  --m3-surface: oklch(97% 0.012 178);
  --m3-on-surface: oklch(16% 0.02 190);
  --m3-surface-variant: oklch(90% 0.02 178);
  --m3-on-surface-variant: oklch(38% 0.02 190);
  --m3-surface-container: oklch(94% 0.015 178);
  --m3-surface-container-high: oklch(91% 0.018 178);
  --m3-outline: oklch(55% 0.03 190);
  --m3-outline-variant: oklch(82% 0.03 190);
  --m3-error: #ba1a1a;
  --m3-on-error: #ffffff;
  --m3-error-container: #ffdad6;
  --m3-elevation-1: 0 1px 3px rgba(0,0,0,.12), 0 1px 2px rgba(0,0,0,.08);
  --m3-elevation-2: 0 2px 6px rgba(0,0,0,.14), 0 1px 4px rgba(0,0,0,.10);
  --m3-elevation-3: 0 6px 18px rgba(0,0,0,.16), 0 2px 6px rgba(0,0,0,.12);
  --m3-shape-xs: 8px;
  --m3-shape-sm: 12px;
  --m3-shape-md: 16px;
  --m3-shape-lg: 20px;
  --m3-shape-xl: 24px;
  --m3-shape-2xl: 28px;
  --m3-shape-full: 100vi;
  --m3-dur-short: 150ms;
  --m3-dur-medium: 300ms;
  --m3-ease-standard: cubic-bezier(.4,0,.2,1);
  --m3-ease-emphasized: cubic-bezier(.05,.7,.1,1);
  --m3-bezel: #1a1a1a;
  --m3-brand-gradient: linear-gradient(135deg, var(--m3-primary), oklch(78% 0.15 90));

  position: relative;
  max-width: 1040px;
  margin: 28px auto 44px;
  border: 1px solid var(--m3-outline-variant);
  border-radius: var(--m3-shape-2xl);
  background: var(--m3-surface);
  color: var(--m3-on-surface);
  font-family: Inter, "Microsoft YaHei", system-ui, sans-serif;
  box-shadow: var(--m3-elevation-3);
  overflow: hidden;
  container: monica-demo / inline-size;
}

.monica-demo.is-dark {
  --m3-primary: oklch(80% 0.08 178);
  --m3-on-primary: oklch(20% 0.06 178);
  --m3-primary-container: oklch(28% 0.08 178);
  --m3-on-primary-container: oklch(88% 0.05 178);
  --m3-secondary: oklch(74% 0.04 178);
  --m3-on-secondary: oklch(16% 0.04 178);
  --m3-secondary-container: oklch(26% 0.04 178);
  --m3-on-secondary-container: oklch(86% 0.04 178);
  --m3-tertiary: oklch(76% 0.06 220);
  --m3-on-tertiary: oklch(16% 0.06 220);
  --m3-tertiary-container: oklch(26% 0.06 220);
  --m3-on-tertiary-container: oklch(88% 0.05 220);
  --m3-surface: oklch(15% 0.02 180);
  --m3-on-surface: oklch(93% 0.01 180);
  --m3-surface-variant: oklch(26% 0.02 180);
  --m3-on-surface-variant: oklch(75% 0.02 190);
  --m3-surface-container: oklch(19% 0.018 180);
  --m3-surface-container-high: oklch(23% 0.022 180);
  --m3-outline: oklch(52% 0.03 190);
  --m3-outline-variant: oklch(32% 0.03 190);
  --m3-error: #ffb4ab;
  --m3-on-error: #690005;
  --m3-error-container: #93000a;
  --m3-elevation-1: 0 1px 3px rgba(0,0,0,.4);
  --m3-elevation-2: 0 2px 6px rgba(0,0,0,.5);
  --m3-elevation-3: 0 6px 18px rgba(0,0,0,.6);
  background: oklch(13% 0.02 180);
  border-color: var(--m3-outline-variant);
}

.monica-demo *, .monica-demo *::before, *::after { box-sizing: border-box; }

/* ---- SVG icon sizing ---- */
.nav-icon svg { width: 22px; height: 22px; display: block; }
.icon-button svg { width: 22px; height: 22px; display: block; }
.small-icon svg { width: 18px; height: 18px; display: block; }
.back-btn svg { width: 24px; height: 24px; display: block; }
.search-icon svg { width: 20px; height: 20px; display: block; }
.search-clear svg { width: 18px; height: 18px; display: block; }
.field-copy svg { width: 20px; height: 20px; display: block; }
.group-chevron svg { width: 16px; height: 16px; display: block; }
.inner-star svg { width: 18px; height: 18px; display: block; }
.detail-fav svg { width: 24px; height: 24px; display: block; }
.action-btn svg { width: 15px; height: 15px; display: inline-block; vertical-align: -3px; margin-right: 5px; }
.fab svg { width: 20px; height: 20px; display: inline-block; vertical-align: -5px; margin-right: 6px; }
.gen-copy svg { width: 20px; height: 20px; display: block; }
.gen-btn svg { width: 20px; height: 20px; display: block; }
.summary-icon svg { width: 22px; height: 22px; display: block; }
.brand-mark svg { width: 20px; height: 20px; display: block; }
.color-dot svg { width: 14px; height: 14px; display: block; }

/* ============================================================
   Topline
   ============================================================ */
.demo-topline {
  display: flex; align-items: center; justify-content: space-between;
  min-height: 42px; padding: 0 22px;
  background: var(--m3-surface-container-high);
  border-bottom: 1px solid var(--m3-outline-variant);
  font-size: 12px; color: var(--m3-on-surface-variant);
}
.demo-kicker {
  color: var(--m3-primary); font-weight: 800; letter-spacing: .02em;
}

/* ============================================================
   App Surface (desktop workspace)
   ============================================================ */
.app-surface {
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  min-height: 650px;
  background: var(--m3-surface);
}

/* ============================================================
   Sidebar (Navigation Rail)
   ============================================================ */
.app-sidebar {
  display: flex; flex-direction: column; gap: 4px;
  padding: 15px 12px;
  border-right: 1px solid color-mix(in oklch, var(--m3-outline-variant), transparent 32%);
  background: color-mix(in oklch, var(--m3-surface-variant), var(--m3-surface) 50%);
}
.app-sidebar.collapsed { grid-template-columns: 1fr; }
.sidebar-brand {
  display: flex; align-items: center; gap: 10px;
  min-height: 62px; margin-bottom: 12px; padding: 0 12px;
  border-bottom: 1px solid color-mix(in oklch, var(--m3-outline-variant), transparent 30%);
  color: var(--m3-on-surface);
}
.brand-mark {
  display: grid; width: 34px; height: 34px; border-radius: var(--m3-shape-sm);
  place-items: center;
  background: var(--m3-brand-gradient);
  color: white; font-size: 18px;
}
.sidebar-brand strong { font-size: 18px; font-weight: 700; }
.sidebar-section {
  margin: 5px 0 3px; padding: 0 16px;
  color: var(--m3-on-surface-variant); font-size: 11px; font-weight: 700;
}
.sidebar-footer { margin-top: auto; }

.nav-item {
  display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: 8px; align-items: center;
  min-height: 46px; padding: 0 14px;
  border: 0; border-radius: var(--m3-shape-full);
  background: transparent; color: var(--m3-on-surface-variant);
  text-align: left; cursor: pointer;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.nav-item:hover { background: color-mix(in oklch, var(--m3-primary-container), transparent 58%); }
.nav-item.active {
  color: var(--m3-on-primary-container);
  background: var(--m3-primary-container);
  font-weight: 700;
}
.nav-icon {
  display: grid; width: 30px; height: 24px; border-radius: var(--m3-shape-full);
  place-items: center; font-size: 17px;
}
.nav-item small { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Collapsed sidebar */
.app-sidebar.collapsed { width: 76px; padding-inline: 9px; }
.app-sidebar.collapsed .sidebar-brand { justify-content: center; margin: 0 0 10px; padding: 0; }
.app-sidebar.collapsed .sidebar-brand strong,
.app-sidebar.collapsed .sidebar-section,
.app-sidebar.collapsed .nav-item small { display: none; }
.app-sidebar.collapsed .nav-item { grid-template-columns: 1fr; justify-items: center; padding: 0; }

/* ============================================================
   Main Content Area
   ============================================================ */
.app-main { display: flex; flex-direction: column; min-width: 0; }

/* ============================================================
   Header
   ============================================================ */
.app-header {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  min-height: 88px; padding: 19px 30px;
  border-bottom: 1px solid color-mix(in oklch, var(--m3-outline-variant), transparent 32%);
  background: color-mix(in oklch, var(--m3-surface-container), var(--m3-surface) 28%);
}
.header-left { display: flex; align-items: center; gap: 14px; }
.back-btn {
  width: 40px; height: 40px; border: 0; border-radius: 50%;
  background: transparent; color: var(--m3-primary); font-size: 28px;
  cursor: pointer; display: grid; place-items: center;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.back-btn:hover { background: var(--m3-primary-container); }
.app-header p { margin: 0 0 2px; color: var(--m3-on-surface-variant); font-size: 12px; font-weight: 600; }
.app-header h3 { margin: 0; color: var(--m3-on-surface); font-size: 28px; font-weight: 400; letter-spacing: -.01em; line-height: 1.1; }
.header-actions { display: flex; gap: 6px; }
.icon-button {
  display: grid; width: 42px; height: 42px; padding: 0;
  border: 0; border-radius: 50%; place-items: center;
  background: transparent; color: var(--m3-primary); cursor: pointer; font-size: 22px;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.icon-button:hover { background: var(--m3-primary-container); }
.quick-menu {
  position: absolute; z-index: 5; top: 68px; right: 24px;
  width: 166px; padding: 8px;
  border: 0; border-radius: var(--m3-shape-lg);
  background: var(--m3-surface-container-high);
  box-shadow: var(--m3-elevation-3);
}
.quick-menu button {
  display: block; width: 100%; border: 0; border-radius: var(--m3-shape-sm);
  padding: 10px 14px; background: transparent; color: var(--m3-on-surface);
  text-align: left; cursor: pointer;
}
.quick-menu button:hover { background: var(--m3-primary-container); }

/* ============================================================
   Search Bar
   ============================================================ */
.search-bar {
  display: flex; align-items: center; gap: 10px;
  margin: 18px 30px 0; min-height: 52px;
  border: 0; border-radius: var(--m3-shape-2xl);
  padding: 0 19px;
  background: var(--m3-surface-variant);
  color: var(--m3-on-surface-variant);
  transition: box-shadow var(--m3-dur-short) var(--m3-ease-standard);
}
.search-bar:focus-within { box-shadow: inset 0 0 0 2px var(--m3-primary); }
.search-icon { font-size: 20px; }
.search-bar input {
  min-width: 0; flex: 1; border: 0; outline: 0; padding: 12px 0;
  background: transparent; color: var(--m3-on-surface);
  font: inherit; font-size: 15px;
}
.search-bar input::placeholder { color: var(--m3-on-surface-variant); }
.search-clear {
  border: 0; background: transparent; color: var(--m3-on-surface-variant);
  cursor: pointer; font-size: 22px;
}

/* ============================================================
   App Content
   ============================================================ */
.app-content { padding: 24px 30px 38px; overflow-y: auto; flex: 1; }

/* ============================================================
   Summary Card
   ============================================================ */
.summary-card {
  display: flex; align-items: center; justify-content: space-between;
  min-height: 112px; padding: 20px 22px;
  border: 0; border-radius: var(--m3-shape-xl);
  background: color-mix(in oklch, var(--m3-primary-container), var(--m3-surface-variant) 35%);
}
.summary-info { display: grid; grid-template-columns: 38px auto; column-gap: 12px; align-items: center; }
.summary-icon {
  grid-row: span 2; display: grid; width: 38px; height: 38px;
  border-radius: var(--m3-shape-sm); place-items: center;
  background: var(--m3-primary); color: var(--m3-on-primary);
  font-size: 19px;
}
.summary-card strong { font-size: 28px; font-weight: 400; line-height: 1; }
.summary-card small { color: var(--m3-on-surface-variant); font-size: 11px; }
.summary-stats { display: flex; gap: 18px; color: var(--m3-on-surface-variant); font-size: 13px; }

/* ============================================================
   Section Heading
   ============================================================ */
.section-heading {
  display: flex; justify-content: space-between; align-items: baseline;
  margin: 28px 2px 12px; color: var(--m3-on-surface);
  font-size: 16px; font-weight: 800;
}
.section-heading small { color: var(--m3-on-surface-variant); font-size: 12px; font-weight: 500; }

/* ============================================================
   Group Cards (Stacked Phantom Layers)
   ============================================================ */
.group-list { display: grid; gap: 10px; }
.group-card { position: relative; margin-bottom: 8px; }
.group-card .phantom {
  position: absolute; left: 0; right: 0;
  border-radius: var(--m3-shape-xl);
  background: var(--m3-surface-container);
  border: 1px solid var(--m3-outline-variant);
  transition: all var(--m3-dur-medium) var(--m3-ease-standard);
}
.group-card .phantom.p1 { bottom: -4px; height: 100%; z-index: 0; opacity: .7; scale: .98; }
.group-card .phantom.p2 { bottom: -8px; height: 100%; z-index: -1; opacity: .5; scale: .96; }
.group-card .phantom.p3 { bottom: -12px; height: 100%; z-index: -2; opacity: .3; scale: .94; }

.group-main {
  position: relative; z-index: 1;
  display: flex; align-items: center; gap: 12px;
  min-height: 78px; padding: 12px 16px;
  border: 1px solid color-mix(in oklch, var(--m3-outline-variant), transparent 28%);
  border-radius: var(--m3-shape-xl);
  background: var(--m3-surface-container);
  cursor: pointer;
  transition: all var(--m3-dur-short) var(--m3-ease-standard);
}
.group-main:hover { box-shadow: var(--m3-elevation-2); transform: translateY(-1px); }
.group-icon {
  display: grid; width: 42px; height: 42px; border-radius: var(--m3-shape-md);
  place-items: center;
  background: var(--m3-primary-container); color: var(--m3-on-primary-container);
  font-size: 20px; font-weight: 800;
}
.group-info { min-width: 0; flex: 1; }
.group-info strong { font-size: 15px; display: block; }
.group-info small { color: var(--m3-on-surface-variant); font-size: 12px; }
.group-badge {
  padding: 4px 10px; border-radius: var(--m3-shape-full);
  background: var(--m3-primary-container); color: var(--m3-on-primary-container);
  font-size: 12px; font-weight: 800;
}
.group-chevron { color: var(--m3-on-surface-variant); font-size: 12px; }

.group-inner {
  margin-top: 8px; padding: 8px;
  border: 1px solid color-mix(in oklch, var(--m3-outline), transparent 55%);
  border-radius: var(--m3-shape-md);
  background: color-mix(in oklch, var(--m3-surface-variant), transparent 48%);
}
.inner-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: var(--m3-shape-sm);
  cursor: pointer;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.inner-item:hover { background: var(--m3-surface-container); }
.brand-dot {
  display: grid; width: 36px; height: 36px; border-radius: var(--m3-shape-sm);
  place-items: center; color: white; font-size: 16px; font-weight: 800;
}
.inner-copy { min-width: 0; flex: 1; }
.inner-copy strong { font-size: 14px; display: block; }
.inner-copy span { color: var(--m3-on-surface-variant); font-size: 11px; }
.inner-star {
  border: 0; background: transparent; color: var(--m3-primary);
  cursor: pointer; font-size: 18px;
}

/* ============================================================
   Credential Cards
   ============================================================ */
.credential-list { display: grid; gap: 10px; }
.credential-card { position: relative; overflow: hidden; }
.credential-card .swipe-bg {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: flex-end;
  padding-right: 24px;
  background: var(--m3-error-container); color: var(--m3-error);
  border-radius: var(--m3-shape-xl); font-weight: 800; font-size: 14px;
}
.card-surface {
  position: relative; display: flex; align-items: center; gap: 12px;
  min-height: 78px; padding: 12px 14px;
  border: 1px solid color-mix(in oklch, var(--m3-outline-variant), transparent 28%);
  border-radius: var(--m3-shape-xl);
  background: color-mix(in oklch, var(--m3-surface-container), var(--m3-surface) 15%);
  cursor: pointer; outline-offset: 2px;
  transition: all var(--m3-dur-short) var(--m3-ease-standard);
}
.card-surface:hover {
  transform: translateY(-2px);
  box-shadow: var(--m3-elevation-2);
  background: color-mix(in oklch, var(--m3-primary-container), var(--m3-surface-container) 62%);
}
.credential-card.selected .card-surface {
  background: var(--m3-primary-container);
  border-color: var(--m3-primary);
}
.brand-icon {
  display: grid; width: 42px; height: 42px; border-radius: var(--m3-shape-md);
  place-items: center; color: white; font-size: 19px; font-weight: 800; flex-shrink: 0;
}
.credential-copy { min-width: 0; flex: 1; display: grid; gap: 1px; }
.credential-copy strong { font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.credential-copy span, .credential-copy small {
  overflow: hidden; color: var(--m3-on-surface-variant); font-size: 11px;
  text-overflow: ellipsis; white-space: nowrap;
}
.card-actions { display: flex; flex-shrink: 0; }
.small-icon {
  display: grid; width: 34px; height: 34px; padding: 0;
  border: 0; border-radius: 50%; place-items: center;
  background: transparent; color: var(--m3-on-surface-variant);
  cursor: pointer; font-size: 18px;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.small-icon:hover { background: var(--m3-surface-variant); color: var(--m3-primary); }

/* ============================================================
   Empty state
   ============================================================ */
.empty-state, .page-note { color: var(--m3-on-surface-variant); text-align: center; font-size: 14px; padding: 40px 0; }

/* ============================================================
   Detail View
   ============================================================ */
.detail-view { display: grid; gap: 20px; }
.detail-hero {
  display: flex; align-items: center; gap: 16px;
  padding: 20px; border-radius: var(--m3-shape-xl);
  background: var(--m3-surface-container-high);
}
.detail-brand {
  display: grid; width: 56px; height: 56px; border-radius: var(--m3-shape-md);
  place-items: center; color: white; font-size: 26px; font-weight: 800; flex-shrink: 0;
}
.detail-hero h2 { margin: 0; font-size: 22px; font-weight: 600; }
.detail-hero p { margin: 4px 0 0; color: var(--m3-on-surface-variant); font-size: 13px; }
.detail-fav {
  margin-left: auto; border: 0; background: transparent;
  color: var(--m3-primary); cursor: pointer; font-size: 26px;
}
.detail-fields { display: grid; gap: 2px; }
.detail-field {
  display: grid; grid-template-columns: 80px 1fr 40px; align-items: center; gap: 12px;
  padding: 16px; border-radius: var(--m3-shape-sm);
  cursor: pointer;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.detail-field:hover { background: var(--m3-surface-variant); }
.field-label { color: var(--m3-on-surface-variant); font-size: 12px; font-weight: 600; }
.field-value { font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.field-value.mono { font-family: ui-monospace, "Cascadia Code", Consolas, monospace; letter-spacing: .02em; }
.field-copy { text-align: center; font-size: 16px; opacity: .5; transition: opacity var(--m3-dur-short); }
.field-copy.copied { opacity: 1; color: var(--m3-primary); }
.totp-field .otp-code { color: var(--m3-primary); font-size: 24px; font-weight: 800; letter-spacing: 2px; }
.field-timer { color: var(--m3-error); font-size: 13px; font-weight: 700; text-align: center; }
.detail-actions { display: flex; gap: 10px; margin-top: 8px; }
.detail-actions .action-btn { flex: 0 1 auto; min-height: 40px; padding: 0 18px; font-size: 13px; }

/* ============================================================
   Action Buttons
   ============================================================ */
.action-btn {
  flex: 1; min-height: 48px; border: 0; border-radius: var(--m3-shape-full);
  font: inherit; font-size: 15px; font-weight: 700; cursor: pointer;
  transition: background var(--m3-dur-short) var(--m3-ease-standard);
}
.action-btn.primary { background: var(--m3-primary); color: var(--m3-on-primary); }
.action-btn.primary:hover { background: color-mix(in oklch, var(--m3-primary), black 12%); }
.action-btn.danger { background: var(--m3-error-container); color: var(--m3-error); }
.action-btn.danger:hover { background: color-mix(in oklch, var(--m3-error-container), black 8%); }
.action-btn.ghost {
  background: transparent; color: var(--m3-on-surface-variant);
  border: 1px solid var(--m3-outline);
}
.action-btn.ghost:hover { background: var(--m3-surface-variant); }
.action-btn.full { width: 100%; margin-top: 16px; }

/* ============================================================
   Entry Form (Add/Edit)
   ============================================================ */
.entry-form { display: grid; gap: 18px; max-width: 560px; }
.form-group { display: grid; gap: 6px; }
.form-group label { color: var(--m3-on-surface-variant); font-size: 12px; font-weight: 700; }
.form-group label span { color: var(--m3-error); }
.form-group input, .form-group textarea {
  width: 100%; min-height: 48px; border: 0; border-radius: var(--m3-shape-md);
  padding: 12px 14px; outline: none;
  background: var(--m3-surface-variant); color: var(--m3-on-surface);
  font: inherit; font-size: 15px;
  transition: box-shadow var(--m3-dur-short) var(--m3-ease-standard);
}
.form-group input:focus, .form-group textarea:focus {
  box-shadow: inset 0 0 0 2px var(--m3-primary);
}
.form-group textarea { min-height: 70px; resize: vertical; }
.password-row { display: flex; gap: 8px; }
.password-row input { flex: 1; }
.gen-btn {
  display: grid; width: 48px; height: 48px; border: 0; border-radius: var(--m3-shape-sm);
  place-items: center;
  background: var(--m3-secondary-container); color: var(--m3-on-secondary-container);
  cursor: pointer; font-size: 20px;
  transition: background var(--m3-dur-short);
}
.gen-btn:hover { background: var(--m3-secondary); color: var(--m3-on-secondary); }
.form-actions { display: flex; gap: 12px; margin-top: 8px; }
.form-hint {
  color: var(--m3-on-surface-variant); font-size: 12px; text-align: center; margin: 0;
}

/* ============================================================
   FAB
   ============================================================ */
.fab {
  position: absolute; z-index: 3; right: 30px; bottom: 29px;
  display: flex; align-items: center; gap: 6px;
  min-width: 138px; height: 48px;
  border: 0; border-radius: var(--m3-shape-full);
  padding: 0 20px;
  background: var(--m3-primary); color: var(--m3-on-primary);
  cursor: pointer; font: inherit; font-size: 14px; font-weight: 700;
  box-shadow: var(--m3-elevation-3);
  transition: box-shadow var(--m3-dur-short) var(--m3-ease-standard),
              transform var(--m3-dur-short) var(--m3-ease-standard);
}
.fab:hover { box-shadow: 0 8px 24px rgba(0,0,0,.24); transform: translateY(-1px); }
.fab-label { white-space: nowrap; }
/* ============================================================
   Multi-select bar
   ============================================================ */
.multi-bar {
  position: absolute; z-index: 10; bottom: 0; left: 0; right: 0;
  display: flex; align-items: center; gap: 8px;
  min-height: 56px; padding: 0 20px;
  background: var(--m3-surface-container-high);
  border-top: 1px solid var(--m3-outline-variant);
  box-shadow: var(--m3-elevation-2);
  overflow-x: auto;
}
.multi-bar button {
  flex-shrink: 0; border: 0; border-radius: var(--m3-shape-full);
  padding: 8px 16px; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer;
  background: var(--m3-surface-variant); color: var(--m3-on-surface);
  transition: background var(--m3-dur-short);
}
.multi-bar button:hover { background: var(--m3-primary-container); }
.multi-bar button.danger { background: var(--m3-error-container); color: var(--m3-error); }
.multi-bar button.danger:hover { background: var(--m3-error); color: var(--m3-on-error); }
.multi-count { color: var(--m3-primary); font-weight: 800; font-size: 14px; white-space: nowrap; margin-right: 8px; }

/* ============================================================
   Authenticator
   ============================================================ */
.auth-hero {
  display: grid; gap: 4px; margin-bottom: 16px; padding: 18px;
  border-radius: var(--m3-shape-xl); background: var(--m3-surface-variant);
}
.auth-hero span { font-weight: 800; font-size: 16px; }
.auth-hero small { color: var(--m3-on-surface-variant); font-size: 13px; }
.otp-list { display: grid; gap: 8px; }
.otp-card {
  display: grid; grid-template-columns: 40px 1fr auto; align-items: center; gap: 12px;
  padding: 14px; border: 1px solid var(--m3-outline-variant);
  border-radius: var(--m3-shape-xl); background: var(--m3-surface-container);
  cursor: pointer; position: relative; overflow: hidden;
  transition: all var(--m3-dur-short) var(--m3-ease-standard);
}
.otp-card:hover { box-shadow: var(--m3-elevation-1); transform: translateY(-1px); }
.otp-logo {
  display: grid; width: 38px; height: 38px; border-radius: 50%;
  place-items: center; color: #fff; font-weight: 800; font-size: 18px;
}
.otp-info strong { font-size: 15px; display: block; }
.otp-info small { color: var(--m3-on-surface-variant); font-size: 11px; }
.otp-right { text-align: right; }
.otp-code { color: var(--m3-primary); font-family: ui-monospace, monospace; font-size: 22px; font-weight: 800; letter-spacing: 1px; }
.otp-timer { color: var(--m3-on-surface-variant); font-size: 11px; font-weight: 600; }
.otp-progress { height: 3px; margin-top: 6px; border-radius: 2px; background: var(--m3-surface-variant); overflow: hidden; }
.otp-progress i { display: block; height: 100%; background: var(--m3-primary); border-radius: 2px; transition: width 1s linear; }
/* Warning state — last 5 seconds */
.otp-card.warning .otp-code { color: var(--m3-error); }
.otp-card.warning .otp-progress i { background: var(--m3-error); }
.otp-card.warning .otp-timer { color: var(--m3-error); font-weight: 800; }
.add-otp-btn {
  margin-top: 16px; width: 100%; min-height: 48px; border: 1px dashed var(--m3-outline);
  border-radius: var(--m3-shape-lg); background: transparent; color: var(--m3-primary);
  font: inherit; font-size: 15px; font-weight: 700; cursor: pointer;
  transition: background var(--m3-dur-short);
}
.add-otp-btn:hover { background: var(--m3-primary-container); }

/* ============================================================
   Cards (Bank Cards)
   ============================================================ */
.card-section { margin-bottom: 20px; }
.card-section .section-heading { margin-top: 0; }
.bank-card {
  display: grid; gap: 16px; padding: 20px; border-radius: var(--m3-shape-xl);
  margin-bottom: 10px; color: white; box-shadow: var(--m3-elevation-2);
}
.bank-card small { opacity: .82; font-size: 12px; }
.bank-card b { font-family: ui-monospace, monospace; font-size: 20px; letter-spacing: 1px; }
.bank-bottom { display: flex; justify-content: space-between; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.doc-card {
  display: flex; align-items: center; gap: 14px;
  padding: 16px; border: 1px solid var(--m3-outline-variant);
  border-radius: var(--m3-shape-xl); background: var(--m3-surface-container);
  margin-bottom: 8px;
}
.doc-icon { font-size: 28px; }
.doc-card strong { font-size: 15px; display: block; }
.doc-card small { color: var(--m3-on-surface-variant); font-size: 12px; }

/* ============================================================
   Notes
   ============================================================ */
.note-card {
  display: flex; align-items: flex-start; gap: 14px;
  padding: 17px; border: 1px solid var(--m3-outline-variant);
  border-radius: var(--m3-shape-xl); background: var(--m3-surface-container);
  margin-bottom: 8px; cursor: pointer;
  transition: all var(--m3-dur-short) var(--m3-ease-standard);
}
.note-card:hover { box-shadow: var(--m3-elevation-1); }
.note-accent { font-size: 22px; margin-top: 2px; }
.note-body { min-width: 0; }
.note-body strong { font-size: 15px; display: block; }
.note-body small { color: var(--m3-on-surface-variant); font-size: 12px; display: block; margin-top: 4px; }
.note-time { color: var(--m3-on-surface-variant); font-size: 11px; margin-top: 6px; display: block; }

/* ============================================================
   Send
   ============================================================ */
.send-card {
  display: flex; align-items: center; gap: 14px;
  padding: 16px; border: 1px solid var(--m3-outline-variant);
  border-radius: var(--m3-shape-xl); background: var(--m3-surface-container);
  margin-bottom: 8px;
}
.send-icon {
  display: grid; width: 40px; height: 40px; border-radius: 50%;
  place-items: center; background: var(--m3-primary-container);
  color: var(--m3-on-primary-container); font-size: 20px;
}
.send-info { flex: 1; }
.send-info strong { font-size: 15px; display: block; }
.send-info small { color: var(--m3-on-surface-variant); font-size: 12px; }
.send-status {
  padding: 4px 12px; border-radius: var(--m3-shape-full);
  background: var(--m3-primary-container); color: var(--m3-on-primary-container);
  font-size: 12px; font-weight: 700;
}

/* ============================================================
   Steam
   ============================================================ */
.steam-card {
  padding: 22px; border-radius: var(--m3-shape-xl);
  background: linear-gradient(135deg, #1b4f78, #0e2a3f);
  color: white; box-shadow: var(--m3-elevation-2);
}
.steam-header { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.steam-avatar {
  display: grid; width: 48px; height: 48px; border-radius: 50%;
  place-items: center; background: rgba(255,255,255,.2);
  font-size: 22px; font-weight: 800;
}
.steam-header strong { font-size: 18px; display: block; }
.steam-header small { opacity: .75; font-size: 12px; }
.steam-stats { display: flex; gap: 24px; }
.steam-stat { text-align: center; }
.steam-stat b { font-size: 24px; display: block; }
.steam-stat small { opacity: .7; font-size: 12px; }

/* ============================================================
   Generator
   ============================================================ */
.gen-tabs { display: flex; gap: 4px; margin-bottom: 16px; overflow-x: auto; }
.gen-tab {
  flex-shrink: 0; border: 0; border-radius: var(--m3-shape-full);
  padding: 10px 20px; background: var(--m3-surface-variant);
  color: var(--m3-on-surface-variant); font: inherit; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all var(--m3-dur-short);
}
.gen-tab.active { background: var(--m3-primary-container); color: var(--m3-on-primary-container); font-weight: 800; }
.gen-tab:hover:not(.active) { background: var(--m3-surface-container-high); }
.gen-output-card {
  display: flex; align-items: center; gap: 12px;
  padding: 22px; border-radius: var(--m3-shape-xl);
  background: var(--m3-primary-container);
  box-shadow: var(--m3-elevation-1);
}
.gen-output {
  flex: 1; overflow-wrap: anywhere;
  color: var(--m3-on-primary-container);
  font-family: ui-monospace, monospace; font-size: 21px; font-weight: 800;
}
.gen-copy {
  display: grid; width: 44px; height: 44px; border: 0; border-radius: 50%;
  place-items: center; background: var(--m3-primary); color: var(--m3-on-primary);
  cursor: pointer; font-size: 18px; flex-shrink: 0;
  transition: background var(--m3-dur-short);
}
.gen-copy:hover { background: color-mix(in oklch, var(--m3-primary), black 16%); }
.gen-options {
  margin-top: 16px; padding: 16px; border-radius: var(--m3-shape-xl);
  background: var(--m3-surface-container);
}
.gen-option { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.gen-option span { color: var(--m3-on-surface-variant); font-size: 13px; width: 36px; }
.gen-option b { font-size: 14px; width: 28px; text-align: center; }
.gen-option input { flex: 1; accent-color: var(--m3-primary); }
.gen-toggles { display: flex; gap: 6px; flex-wrap: wrap; }
.gen-toggles button {
  border: 1px solid var(--m3-outline); border-radius: var(--m3-shape-xs);
  padding: 8px 12px; background: transparent; color: var(--m3-on-surface-variant);
  font: inherit; font-size: 12px; font-weight: 700; cursor: pointer;
  transition: all var(--m3-dur-short);
}
.gen-toggles button.on { background: var(--m3-primary); color: var(--m3-on-primary); border-color: var(--m3-primary); }
.gen-history { margin-top: 20px; }
.gen-history .section-heading { margin-top: 0; font-size: 14px; }
.gen-hist-item {
  padding: 12px 16px; border-radius: var(--m3-shape-sm);
  font-family: ui-monospace, monospace; font-size: 13px; cursor: pointer;
  color: var(--m3-on-surface-variant);
  transition: background var(--m3-dur-short);
}
.gen-hist-item:hover { background: var(--m3-surface-variant); color: var(--m3-on-surface); }

/* ============================================================
   Settings
   ============================================================ */
.settings-group { display: grid; gap: 2px; margin-bottom: 24px; }
.settings-group > span {
  margin: 0 8px 8px; color: var(--m3-primary); font-size: 12px; font-weight: 800;
  letter-spacing: .02em;
}
.settings-group button {
  display: flex; justify-content: space-between; align-items: center;
  border: 0; padding: 14px 16px; background: var(--m3-surface-container);
  color: var(--m3-on-surface); cursor: pointer; text-align: left;
  font: inherit; font-size: 14px;
  transition: background var(--m3-dur-short);
}
.settings-group button:first-of-type { border-radius: var(--m3-shape-lg) var(--m3-shape-lg) 0 0; }
.settings-group button:last-of-type { border-radius: 0 0 var(--m3-shape-lg) var(--m3-shape-lg); }
.settings-group button:only-of-type { border-radius: var(--m3-shape-lg); }
.settings-group button:hover { background: var(--m3-surface-container-high); }
.settings-group button b { color: var(--m3-on-surface-variant); font-weight: 500; }
.color-grid {
  display: flex; flex-wrap: wrap; gap: 10px;
  padding: 14px 16px; border-radius: var(--m3-shape-lg);
  background: var(--m3-surface-container);
}
.color-dot {
  width: 36px; height: 36px; min-width: 36px; min-height: 36px;
  border: 3px solid transparent;
  border-radius: 50%; cursor: pointer; padding: 0; position: relative;
  transition: all var(--m3-dur-short) var(--m3-ease-standard);
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  box-sizing: border-box;
}
.color-dot:hover { scale: 1.15; }
.color-dot.active { border-color: var(--m3-on-surface); box-shadow: 0 0 0 2px var(--m3-primary); }
.color-dot span {
  color: #fff; font-weight: 800; font-size: 13px;
  text-shadow: 0 1px 2px rgba(0,0,0,.5);
  line-height: 1;
}

/* ============================================================
   Toast
   ============================================================ */
.toast {
  position: absolute; z-index: 20; bottom: 30px; left: 50%;
  margin: 0; border-radius: var(--m3-shape-full);
  padding: 12px 22px;
  background: var(--m3-on-surface); color: var(--m3-surface);
  font-size: 13px; font-weight: 600; white-space: nowrap;
  box-shadow: var(--m3-elevation-3);
  translate: -50% 0;
}
.toast-enter-active { transition: opacity .2s ease, transform .25s var(--m3-ease-emphasized); }
.toast-leave-active { transition: opacity .15s ease, transform .15s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(12px); }

/* ============================================================
   Responsive — Mobile / Narrow
   ============================================================ */
@media (max-width: 760px) {
  .monica-demo { margin-inline: 0; border-radius: var(--m3-shape-lg); }
  .app-surface { grid-template-columns: 1fr; grid-template-rows: 1fr auto; }
  .app-sidebar {
    position: relative; z-index: 10; grid-row: 2;
    display: flex; flex-direction: row; justify-content: space-around; gap: 0;
    min-height: 72px; padding: 6px 4px env(safe-area-inset-bottom, 8px);
    border-right: 0; border-top: 1px solid var(--m3-outline-variant);
    background: var(--m3-surface-container-high);
    box-shadow: var(--m3-elevation-2);
  }
  .app-sidebar .sidebar-brand,
  .app-sidebar .sidebar-section,
  .app-sidebar .sidebar-footer,
  .app-sidebar.collapsed .sidebar-brand strong,
  .app-sidebar.collapsed .sidebar-section { display: none; }
  .nav-item {
    display: flex; flex-direction: column; align-items: center; gap: 2px;
    min-height: auto; padding: 6px 0; border-radius: var(--m3-shape-xs);
  }
  .nav-item small { font-size: 9px; }
  .app-content { padding: 16px 18px 20px; }
  .nav-icon { width: 28px; height: 20px; font-size: 16px; }
  .app-header { min-height: auto; padding: 16px 18px; }
  .app-header h3 { font-size: 22px; }
  .app-content { padding: 16px 18px 90px; }
  .search-bar { margin: 12px 18px 0; }
  .fab { right: 18px; bottom: 88px; min-width: 48px; width: 48px; padding: 0; justify-content: center; }
  .fab svg { margin: 0; }
  .fab-label { display: none; }
  .summary-card { min-height: auto; flex-direction: column; align-items: flex-start; gap: 12px; padding: 16px; }
  .detail-field { grid-template-columns: 60px 1fr 32px; gap: 8px; padding: 12px; }
  .otp-card { padding: 12px; }
  .otp-code { font-size: 18px; }
  .color-grid { grid-template-columns: repeat(4, 1fr); gap: 10px; }
  .gen-tabs { gap: 2px; }
  .gen-tab { padding: 8px 14px; font-size: 12px; }
  .steam-stats { gap: 16px; }
}

@media (max-width: 480px) {
  .monica-demo { margin-inline: -8px; border-radius: var(--m3-shape-lg); }
  .app-content { padding-inline: 14px; }
  .bank-card { padding: 16px; }
  .gen-output { font-size: 17px; }
  .detail-hero h2 { font-size: 19px; }
}
</style>
