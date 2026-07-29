<script setup lang="ts">

import {nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue";
import RecursiveMenuItem from "../../components/RecursiveMenuItem.vue";
import router from "../../router";
import {ApiGlobalConfig} from "../../api/ApiGlobalConfig.ts";
import Request from "../../api/Request.ts";

const collapsed = ref(false);
const isMobile = ref(false);
const mobileNavOpen = ref(false);
let viewportQuery: MediaQueryList | null = null;

const syncViewport = () => {
  const mobile = typeof window !== 'undefined' && window.matchMedia('(max-width: 860px)').matches;
  const wasMobile = isMobile.value;
  isMobile.value = mobile;
  if (mobile) collapsed.value = true;
  if (!mobile) {
    mobileNavOpen.value = false;
    if (wasMobile) collapsed.value = false;
  }
};

const onCollapse = () => {
  if (isMobile.value) {
    mobileNavOpen.value = !mobileNavOpen.value;
    collapsed.value = !mobileNavOpen.value;
    return;
  }
  collapsed.value = !collapsed.value;
};
const menus = ref([] as any[]);
const breadcrumbList = ref<string[]>(['系统后台', '首页']);
const selectedKeys = ref<string[]>(['/dashboard']);
const openKeys = ref<string[]>([]);
const userInfo = ref<any>(null);
const historyPages = ref<any[]>([{
  path: '/dashboard',
  meta: { title: '首页', icon: 'home' }
}]);

menus.value = [
  {
    path: '/dashboard',
    meta: { title: '首页', icon: 'home' }
  },
  {
    path: '/player',
    meta: { title: '玩家管理', icon: 'home' },
    children: [
      {
        path: 'accounts',
        meta: { title: '账号管理', icon: 'home' },
      },
      {
        path: 'roles',
        meta: { title: '角色管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/guild',
    meta: { title: '公会管理', icon: 'role' }
  },
  {
    path: '/monitor',
    meta: { title: '服务器监控', icon: 'dashboard' }
  },
  {
    path: '/reward',
    meta: { title: '奖励管理', icon: 'home' },
    children: [
      {
        path: 'tasks',
        meta: { title: '定时任务管理', icon: 'home' },
      },
      {
        path: 'global',
        meta: { title: '全服奖励发放', icon: 'home' },
      }
    ]
  },
  {
    path: '/online-reward',
    meta: { title: '在线泡点', icon: 'clock-circle' }
  },
  {
    path: '/mail',
    meta: { title: '邮件管理', icon: 'home' },
    children: [
      {
        path: 'query',
        meta: { title: '邮件查询', icon: 'home' },
      },
      {
        path: 'player',
        meta: { title: '玩家邮件管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/database',
    meta: { title: '数据库管理', icon: 'home' },
    children: [
      {
        path: 'backup',
        meta: { title: '备份与恢复', icon: 'home' },
      }
    ]
  },
  {
    path: '/pvf',
    meta: { title: 'PVF管理', icon: 'home' },
    children: [
      {
        path: 'manager',
        meta: { title: 'PVF管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/login',
    meta: { title: '登录器管理', icon: 'home' },
    children: [
      {
        path: 'config',
        meta: { title: '登录器配置', icon: 'home' },
      },
      {
        path: 'versions',
        meta: { title: '版本管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/assist',
    meta: { title: '客户端管理', icon: 'home' }
  }
];

const normalizeAdminPath = (path: string) => {
  const normalized = path.replace(/^\/admin/, '');
  return normalized || '/dashboard';
};

onMounted(() => {
  syncViewport();
  viewportQuery = window.matchMedia('(max-width: 860px)');
  viewportQuery.addEventListener?.('change', syncViewport);
  nextTick(() => {
    console.log('Menus:', menus.value);
    // 初始化面包屑导航
    let path = router.currentRoute.value.path;
    if (!path || path.trim() === '/'){
      path = window.location.pathname;
    }
    onClickMenuItem(normalizeAdminPath(path));
  });
});

onBeforeUnmount(() => {
  viewportQuery?.removeEventListener?.('change', syncViewport);
});


const onClickMenuItem = (item: any, _ev?: Event) => {
  const tempBreadcrumbList = ['系统后台'] as string[];
  const itemObj = searchByFullPath(item, menus.value, tempBreadcrumbList);
  if (!itemObj){
    throw new Error(`Menu item not found for path: ${item}`);
  }
  const path = '/admin' + (item.startsWith('/') ? item : `/${item}`);
  breadcrumbList.value = tempBreadcrumbList;
  selectedKeys.value = [item];
  mobileNavOpen.value = false;
  // 添加到历史记录标签页
  const exists = historyPages.value.find(page => page.path === item);
  if (!exists) {
    historyPages.value.push({
      path: item,
      meta: { title: itemObj.meta?.title || '未命名', icon: itemObj.meta?.icon || '' }
    });
  }
  if (router.currentRoute.value.fullPath !== path) {
    router.push({ path });
  }

  // 如果是嵌套路由，展开父级菜单（兼容 '/pvf/manager' 这种深层路径）
  const parts = item.split('/').filter(Boolean);
  const parentPath = parts.length > 1 ? `/${parts[0]}` : '';
  if (parentPath) {
    const parentItem = menus.value.find(menu => menu.path === parentPath);
    if (parentItem) {
      openKeys.value = [parentPath];
    }
  }
};

watch(() => router.currentRoute.value.path, (path) => {
  if (!path.startsWith('/admin/')) return;
  const item = normalizeAdminPath(path);
  if (selectedKeys.value[0] !== item) onClickMenuItem(item);
});

const searchByFullPath = (path: string, routes: any[], tempBreadcrumbList: string[], parentPath = ''): any | null => {
  if (!tempBreadcrumbList){
    tempBreadcrumbList = ['系统后台'];
  }
  for (let route of routes) {
    const routePath = route.path.startsWith('/') ? route.path : `${parentPath}/${route.path}`;
    const fullPath = route.fullPath || routePath;
    if (fullPath === path) {
      // 添加当前路由标题到面包屑列表
      tempBreadcrumbList.push(route.meta?.title || '');
      return route;
    }
    if (route.children && route.children.length > 0) {
      const found = searchByFullPath(path, route.children, tempBreadcrumbList, fullPath);
      if (found) {
        // 添加当前路由标题到面包屑列表
        // tempBreadcrumbList.unshift(route.meta?.title || '');
        tempBreadcrumbList.splice(1, 0, route.meta?.title || '');
        return found;
      }
    }
  }
  return null;
};

const imgBaseUrl = ApiGlobalConfig.imageViewer.baseURL



const onDeleteTab = (tabKey: string | number, _ev?: Event) => {
  const key = String(tabKey);
  const index = historyPages.value.findIndex(page => page.path === key);
  if (index !== -1) {
    historyPages.value.splice(index, 1);
    // 如果关闭的是当前激活的标签页，切换到最后一个标签页
    if (key === selectedKeys.value[0]) {
      const lastPage = historyPages.value[historyPages.value.length - 1];
      if (lastPage) {
        onClickMenuItem(lastPage.path);
      }
    }
  }
};

const logout = () => {
  localStorage.removeItem('token')
  router.replace('/login')
}

// TODO 临时先随便请求个接口，触发后端验证token逻辑，获取用户信息
Request.get('api/v1/account?page=1&pageSize=1')
</script>

<template>
<div class="admin-page">
  <a-layout class="admin-layout">
    <a-layout-sider
        class="admin-sider"
        :class="{ 'mobile-open': mobileNavOpen }"
        hide-trigger
        collapsible
        :collapsed="collapsed"
        theme="dark"
    >
      <a-menu class="left-nav"
          :selected-keys="selectedKeys"
          v-model:open-keys="openKeys"
          :style="{ width: '100%' }"
          theme="dark"
          @menuItemClick="onClickMenuItem"
      >

        <a-menu-item key="0" :style="{ padding: 0}" disabled>
          <div class="logo">
            <img src="../../assets/images/icon.png" alt="Logo" />
            <span v-if="!collapsed">
              DNF-Admin
            </span>
          </div>
        </a-menu-item>
        <recursive-menu-item
            v-for="route in menus"
            :key="route.path"
            :item="route"
        />
      </a-menu>
    </a-layout-sider>
    <button
        v-if="mobileNavOpen"
        class="mobile-nav-backdrop"
        type="button"
        aria-label="关闭导航"
        @click="mobileNavOpen = false"
    ></button>
    <a-layout class="layout-right">
      <a-layout-header class="layout-header">
        <button class="toggle-button" type="button" aria-label="切换导航" @click="onCollapse">
          <icon-menu-unfold v-if="collapsed" />
          <icon-menu-fold v-else />
        </button>
        <div class="breadcrumb-container">
          <a-breadcrumb :style="{ margin: '16px 0' }">
            <a-breadcrumb-item v-for="item in breadcrumbList">{{item}}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="right">
          <a-dropdown trigger="hover">
            <div class="right-user-info">
              <div class="user-info-left">
                <a-avatar v-if="userInfo && userInfo.avatar" :size="40">
                  <!-- 环境变量 -->
                  <img
                      alt="avatar"
                      :src="imgBaseUrl + '/' + userInfo.avatar"
                  />
                </a-avatar>
                <a-avatar v-else-if="userInfo" :size="40">
                  {{userInfo.nickName ? userInfo.nickName : userInfo.userName}}
                </a-avatar>
                <a-avatar v-else :size="40">
                  <icon-user />
                </a-avatar>
              </div>
              <div class="user-info-right" style="color: white; margin-left: 8px;">
                <div>{{userInfo ? (userInfo.nickName ? userInfo.nickName : userInfo.userName) : 'GM管理员'}}</div>
                <div style="font-size: 12px;" v-if="userInfo">{{userInfo.email ? userInfo.email : (userInfo.phonenumber ? userInfo.phonenumber : '')}}</div>
              </div>
            </div>
            <template #content>
              <a-doption>
                <icon-user style="margin-right: 10px"/>个人信息
              </a-doption>
              <a-doption>
                <icon-lock style="margin-right: 10px"/>修改密码
              </a-doption>
              <a-doption @click="logout">
                <icon-poweroff style="margin-right: 10px"/>退出登录
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content class="layout-content">
        <a-tabs class="layout-tabs" hide-content closable editable @delete="onDeleteTab" v-model:active-key="selectedKeys[0]" @change="(key: string | number) => onClickMenuItem(String(key))">
          <a-tab-pane v-for="page in historyPages" :key="page.path" :title="page.meta.title" :closable="page.path !== selectedKeys[0]">
          </a-tab-pane>
        </a-tabs>
        <!-- 动态路由视图 -->
        <div class="content-view">
          <a-scrollbar style="height: 100%;">
            <router-view :key="router.currentRoute.value.fullPath" class="router-viewer"/>
          </a-scrollbar>
        </div>
      </a-layout-content>
<!--      <a-layout-footer>-->
<!--        Footer-->
<!--      </a-layout-footer>-->
    </a-layout>
  </a-layout>
</div>
</template>

<style scoped lang="less">
.admin-page {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-height: 100vh;
  min-height: 100dvh;
  overflow: hidden;
  background: var(--gm-bg);

  .admin-layout,
  .layout-right,
  .layout-content,
  .content-view {
    min-width: 0;
    min-height: 0;
  }

  .admin-layout {
    width: 100%;
    height: 100%;
    overflow: hidden;
  }

  .admin-sider {
    z-index: var(--gm-z-drawer);
    flex: 0 0 auto;
    border-right: 1px solid var(--gm-rule);
    background: #0a111d;
    transition: transform 180ms ease, box-shadow 180ms ease;

    .left-nav {
      height: 100%;
      overflow-y: auto;
      scrollbar-width: thin;
      scrollbar-color: rgba(151, 174, 204, .32) transparent;
    }

    .logo {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 64px;
      flex-direction: row;
      gap: 9px;
      border-bottom: 1px solid var(--gm-rule);

      img {
        flex: 0 0 32px;
        width: 32px;
        height: 32px;
      }

      span {
        color: var(--gm-text);
        font-size: 19px;
        font-weight: 750;
        letter-spacing: .01em;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    :deep(.arco-menu) {
      background: transparent;
    }

    :deep(.arco-menu-item-disabled:first-child) {
      min-height: 64px;
      margin: 0 !important;
      padding: 0 !important;
      border-radius: 0;
      opacity: 1;
      background: transparent !important;
    }

    :deep(.arco-menu-item),
    :deep(.arco-menu-inline-header) {
      min-height: 42px;
      margin: 3px 8px;
      border-radius: 6px;
      color: var(--gm-muted);
      transition: color 160ms ease, background-color 160ms ease;
    }

    :deep(.arco-menu-item:hover),
    :deep(.arco-menu-inline-header:hover) {
      color: var(--gm-text);
      background: var(--gm-surface-raised);
    }

    :deep(.arco-menu-selected) {
      color: #061118 !important;
      background: var(--gm-cyan) !important;
      box-shadow: 0 0 18px rgba(77, 228, 210, .16);
    }

    :deep(.arco-menu-selected .arco-menu-icon),
    :deep(.arco-menu-selected .arco-menu-title) {
      color: #061118 !important;
    }

    :deep(.arco-menu-inline-header.arco-menu-selected) {
      color: var(--gm-text) !important;
      background: var(--gm-surface-raised) !important;
      box-shadow: inset 3px 0 0 var(--gm-cyan);
    }
  }

  .layout-right {
    display: flex;
    height: 100%;
    overflow: hidden;
  }

  .layout-header {
    position: relative;
    z-index: var(--gm-z-header);
    display: flex;
    align-items: center;
    flex: 0 0 58px;
    height: 58px;
    min-width: 0;
    padding: 0 18px;
    border-bottom: 1px solid var(--gm-rule);
    background: #0a111d;

    .toggle-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex: 0 0 34px;
      width: 34px;
      height: 34px;
      padding: 0;
      border: 1px solid transparent;
      border-radius: 6px;
      color: var(--gm-text);
      background: transparent;
      cursor: pointer;

      &:hover {
        border-color: var(--gm-rule-strong);
        color: var(--gm-cyan);
        background: var(--gm-cyan-soft);
      }
    }

    .breadcrumb-container {
      display: flex;
      flex: 1 1 auto;
      min-width: 0;
      margin-left: 12px;
      overflow: hidden;

      :deep(.arco-breadcrumb) {
        min-width: 0;
        overflow: hidden;
        white-space: nowrap;
      }

      :deep(.arco-breadcrumb-item) {
        color: var(--gm-muted) !important;

        &:last-child {
          color: var(--gm-text) !important;
        }
      }
    }

    .right {
      display: flex;
      align-items: center;
      flex: 0 0 auto;
      min-width: 0;
      margin-left: auto;

      .right-user-info {
        display: flex;
        align-items: center;
        min-width: 0;
        padding: 4px 6px;
        border-radius: 6px;
        cursor: pointer;

        &:hover {
          background: var(--gm-surface-raised);
        }

        .user-info-left {
          display: flex;
          align-items: center;
          justify-content: center;
          flex: 0 0 auto;
        }

        .user-info-right {
          display: flex;
          flex-direction: column;
          justify-content: center;
          min-width: 0;
          max-width: 180px;
          margin-left: 8px !important;
          color: var(--gm-text) !important;

          > div {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }
    }
  }

  .layout-content {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    overflow: hidden;

    .layout-tabs {
      z-index: var(--gm-z-tabs);
      flex: 0 0 48px;
      min-width: 0;
      border-bottom: 1px solid var(--gm-rule);
      background: var(--gm-surface);

      :deep(.arco-tabs-nav) {
        min-width: 0;
        overflow-x: auto;
        scrollbar-width: none;
      }

      :deep(.arco-tabs-nav::-webkit-scrollbar) {
        display: none;
      }

      :deep(.arco-tabs-tab) {
        max-width: 180px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: var(--gm-muted);
      }

      :deep(.arco-tabs-tab-active) {
        color: var(--gm-cyan);
      }

      :deep(.arco-tabs-content) {
        padding-top: 0 !important;
      }
    }

    .content-view {
      flex: 1 1 auto;
      overflow: hidden;

      :deep(.arco-scrollbar) {
        height: 100%;
      }

      :deep(.arco-scrollbar-container) {
        min-width: 0;
        min-height: 100%;
        overflow: auto;
      }
    }
  }

  .mobile-nav-backdrop {
    position: fixed;
    z-index: calc(var(--gm-z-drawer) - 1);
    inset: 0;
    display: none;
    width: 100%;
    height: 100%;
    padding: 0;
    border: 0;
    background: rgba(2, 7, 13, .68);
  }
}

@media (max-width: 860px) {
  .admin-page {
    .admin-sider {
      position: fixed;
      top: 0;
      bottom: 0;
      left: 0;
      width: 236px !important;
      transform: translateX(-102%);
      box-shadow: none;

      &.mobile-open {
        transform: translateX(0);
        box-shadow: 18px 0 42px rgba(0, 0, 0, .34);
      }
    }

    .mobile-nav-backdrop {
      display: block;
    }

    .layout-header {
      padding: 0 12px;

      .breadcrumb-container {
        margin-left: 8px;
      }
    }

    .layout-tabs :deep(.arco-tabs-tab) {
      max-width: 132px;
    }
  }
}

@media (max-width: 640px) {
  .admin-page {
    .layout-header {
      .breadcrumb-container {
        display: none;
      }

      .right-user-info {
        .user-info-right {
          display: none !important;
        }
      }
    }

    .layout-tabs {
      flex-basis: 44px;
    }

    .content-view :deep(.arco-scrollbar-container) {
      overflow-x: hidden;
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-page .admin-sider,
  .admin-page .arco-btn,
  .admin-page .toggle-button {
    transition: none;
  }
}
</style>
