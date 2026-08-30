<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <AppSidebar />

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- TOP BAR -->
      <header class="top-bar">
        <div class="tb-pill">
          <MaterialIcon class="msr primary" name="location_on" />
          <span class="tb-pill-text">{{ location.label }}</span>
          <MaterialIcon class="msr tb-pill-caret" name="keyboard_arrow_down" />
        </div>
        <div class="tb-search">
          <MaterialIcon class="msr" name="search" />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索影片、演员、导演..."
            @keyup.enter="onSearch"
          />
        </div>
        <div class="tb-pill">
          <MaterialIcon class="msr" name="calendar_month" />
          <span class="tb-pill-text">{{ todayLabel }}</span>
        </div>
        <div class="tb-icon-group">
          <button class="tb-icon" @click="tip('通知')">
            <MaterialIcon class="msr" name="notifications" />
            <span class="tb-dot"></span>
          </button>
          <button class="tb-icon" @click="tip('购物车')">
            <MaterialIcon class="msr" name="shopping_cart" />
          </button>
        </div>
        <button class="tb-cta" @click="tip('扫码取票')">
          <MaterialIcon class="msr" name="qr_code_scanner" />
          <span>扫码取票</span>
        </button>
      </header>

      <!-- CONTENT -->
      <div class="content">
        <!-- ============ MOVIES HERO ============ -->
        <section class="m-hero" v-if="!isSearching">
          <div class="m-hero-main">
            <div class="m-hero-poster" :style="{ background: cardGradient(0) }">
              <img v-if="heroMovie?.poster" :src="heroMovie.poster" :alt="heroMovie?.title" />
              <div class="m-hero-poster-overlay">
                <span class="m-poster-eng">{{ (heroMovie?.englishTitle || heroMovie?.title || '').toUpperCase() }}</span>
                <span class="m-poster-title">{{ heroMovie?.title }}</span>
              </div>
            </div>
            <div class="m-hero-info">
              <div class="m-hero-eyebrow">
                <MaterialIcon class="msr" name="stars" />
                <span>FEATURED · 编辑精选</span>
              </div>
              <h1 class="m-hero-title">{{ heroMovie?.title }}</h1>
              <p class="m-hero-eng">{{ (heroMovie?.englishTitle || heroMovie?.title || '').toUpperCase() }}</p>
              <div class="m-hero-meta">
                <span class="m-hero-rating">
                  <MaterialIcon class="msr" name="star" />
                  {{ heroMovie?.rating?.toFixed(1) || '9.0' }}
                </span>
                <span class="m-hero-dot">·</span>
                <span>{{ heroMovie?.genre || '剧情' }}</span>
                <span class="m-hero-dot">·</span>
                <span>{{ heroMovie?.duration || 120 }} 分钟</span>
                <span class="m-hero-dot">·</span>
                <span>{{ heroMovie?.releaseDate || '2026' }}</span>
              </div>
              <p class="m-hero-desc">{{ heroMovie?.description || '一部精彩的电影,带你进入不一样的世界。' }}</p>
              <div class="m-hero-tags">
                <span class="m-hero-tag" v-for="t in heroTags" :key="t">#{{ t }}</span>
              </div>
              <div class="m-hero-actions">
                <button class="m-cta primary" @click="heroMovie && goToDetail(heroMovie.id)">
                  <span>立即选座</span>
                  <MaterialIcon class="msr" name="arrow_forward" />
                </button>
                <button class="m-cta ghost" @click="tip('观看预告')">
                  <MaterialIcon class="msr" name="play_circle" />
                  <span>看预告</span>
                </button>
                <WatchlistHeartButton
                  size="large"
                  variant="dark"
                  :active="Boolean(heroMovie && favoriteMovieIds.has(heroMovie.id))"
                  @click="heroMovie && toggleFavorite(heroMovie)"
                />
                <button class="m-cta icon" @click="tip('分享')">
                  <MaterialIcon class="msr" name="share" />
                </button>
              </div>
            </div>
          </div>
          <aside class="m-hero-side">
            <div class="side-head">
              <span class="side-eyebrow"><MaterialIcon class="msr side-icon" name="auto_awesome" />AI 即时洞察</span>
              <div class="side-live" :class="{ loading: insightLoading, error: insightError }">
                <span class="side-live-dot"></span>
                <span>{{ insightLoading ? '更新中' : (insightError ? '已降级' : '实时') }}</span>
                <button class="side-refresh" type="button" aria-label="刷新实时洞察" :disabled="insightLoading" @click="refreshInsight">
                  <MaterialIcon class="msr" name="refresh" />
                </button>
              </div>
            </div>
            <div class="side-match">
              <div class="match-ring">
                <svg viewBox="0 0 56 56" class="ring-svg">
                  <circle cx="28" cy="28" r="24" class="ring-bg" />
                  <circle cx="28" cy="28" r="24" class="ring-fg" :style="{ strokeDasharray: ringDash(matchScore(heroMovie?.id || 1)) }" />
                </svg>
                <span class="match-pct">{{ matchScore(heroMovie?.id || 1) }}%</span>
              </div>
              <div class="match-text">
                <span class="match-label">{{ heroMatchLabel }}</span>
                <span class="match-sub" :title="heroMatchSub">{{ heroMatchSub }}</span>
              </div>
            </div>
            <div class="side-divider"></div>
            <div class="side-stats">
              <div class="side-stat">
                <span class="stat-num">{{ heroBoxOffice }}</span>
                <span class="stat-label">今日票房</span>
              </div>
              <div class="side-stat">
                <span class="stat-num">{{ heroViewerCount }}</span>
                <span class="stat-label">今日观影人次</span>
              </div>
              <div class="side-stat">
                <span class="stat-num">{{ heroOccupancy }}</span>
                <span class="stat-label">今日上座率</span>
              </div>
            </div>
            <div class="side-trend">
              <div class="trend-row">
                <span class="trend-label">近 7 天购票热度</span>
                <span class="trend-up" :class="trendDirection">
                  <MaterialIcon class="msr" :name="trendIcon" />
                  <span>{{ trendText }}</span>
                </span>
              </div>
              <svg class="trend-spark" viewBox="0 0 200 40" preserveAspectRatio="none">
                <polyline
                  :points="sparkPoints"
                  fill="none"
                  stroke="#FF8400"
                  stroke-width="2"
                />
                <polyline
                  :points="sparkAreaPoints"
                  fill="rgba(255, 132, 0, 0.15)"
                  stroke="none"
                />
              </svg>
              <span class="insight-updated">{{ insightUpdatedLabel }}</span>
            </div>
          </aside>
        </section>

        <!-- ============ FILTER BAR ============ -->
        <section class="filter-bar">
          <div class="fb-tabs">
            <button
              v-for="tab in statusTabs"
              :key="tab.value"
              class="fb-tab"
              :class="{ active: tab.value === statusFilter }"
              @click="setStatusFilter(tab.value)"
            >
              <MaterialIcon class="msr" v-if="tab.icon" :name="tab.icon" />
              <span>{{ tab.label }}</span>
              <span v-if="tab.badge" class="fb-tab-badge">{{ tab.badge }}</span>
            </button>
          </div>
          <div class="fb-genres">
            <button
              v-for="g in genres"
              :key="g"
              class="fb-genre"
              :class="{ active: g === activeGenre }"
              @click="activeGenre = g"
            >{{ g }}</button>
          </div>
          <div class="fb-tools">
            <button class="fb-sort" @click="cycleSort">
              <MaterialIcon class="msr" name="sort" />
              <span>{{ sortLabel }}</span>
              <MaterialIcon class="msr" name="keyboard_arrow_down" />
            </button>
            <div class="fb-view">
              <button
                class="fb-view-btn"
                :class="{ active: viewMode === 'grid' }"
                @click="viewMode = 'grid'"
              >
                <MaterialIcon class="msr" name="grid_view" />
              </button>
              <button
                class="fb-view-btn"
                :class="{ active: viewMode === 'list' }"
                @click="viewMode = 'list'"
              >
                <MaterialIcon class="msr" name="view_list" />
              </button>
            </div>
          </div>
        </section>

        <!-- ============ MOVIES GRID ============ -->
        <section class="movies-section">
          <div class="ms-head">
            <div class="ms-title-block">
              <h2 class="ms-title">{{ isHotRanking ? '热映榜单 TOP 10' : (isUpcomingListing ? '即将上映' : (isSearching ? `“${searchKeyword.trim()}” 的搜索结果` : statusLabel)) }}</h2>
              <p class="ms-sub">
                {{ isHotRanking ? `实时热度排行 · 共 ${filteredMovies.length} 部热映影片` : (isUpcomingListing ? `${filteredMovies.length} 部待映影片 · 按上映日期排序` : `${filteredMovies.length} 部影片 · 按 ${sortLabel} 排序`) }}
                <button v-if="isSearching" class="ms-clear" @click="resetFilters">
                  <MaterialIcon class="msr" name="close" />
                  <span>清除搜索</span>
                </button>
              </p>
            </div>
            <div class="ms-summary">
              <div class="ms-chip">
                <MaterialIcon class="msr" name="movie" />
                <span>{{ filteredMovies.length }} 部</span>
              </div>
              <div class="ms-chip">
                <MaterialIcon class="msr" name="star" />
                <span>平均 {{ avgRating }}</span>
              </div>
              <div class="ms-chip ai">
                <MaterialIcon class="msr" name="auto_awesome" />
                <span>AI 优选</span>
              </div>
            </div>
          </div>

          <div
            v-if="viewMode === 'grid' && filteredMovies.length"
            class="movies-grid"
            v-loading="loading"
          >
            <article
              v-for="(m, i) in pagedMovies"
              :key="m.id"
              class="movie-card"
              @click="goToDetail(m.id)"
            >
              <div class="card-poster" :style="{ background: cardGradient(i) }">
                <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                <div class="card-top">
                  <span class="card-badge" :class="{ primary: isHotRanking || m.id === heroMovie?.id }">
                    <span class="dot-now"></span>
                    <span>{{ isHotRanking ? `TOP ${i + 1}` : statusBadge(m) }}</span>
                  </span>
                  <span class="card-rating">
                    <MaterialIcon class="msr" name="star" />
                    <span>{{ m.rating?.toFixed(1) || '8.5' }}</span>
                  </span>
                </div>
                <div class="card-ai">
                  <MaterialIcon class="msr" name="auto_awesome" />
                  <span>{{ matchScore(m.id) }}%</span>
                </div>
              </div>
              <div class="card-title-panel">
                <h3 class="card-title-below">{{ m.title }}</h3>
                <p class="card-eng-below">{{ (m.englishTitle || m.title).toUpperCase() }}</p>
              </div>
              <div class="card-foot">
                <div class="card-meta">
                  <div class="card-genre-row">
                    <MaterialIcon class="msr genre-ico" name="local_fire_department" />
                    <span class="card-genre">{{ m.genre || '剧情' }}</span>
                  </div>
                  <div class="card-price-row">
                    <span class="price-cur">¥</span>
                    <span class="price-num">{{ priceFor(m) }}</span>
                    <span class="price-unit">起</span>
                  </div>
                </div>
                <div class="card-actions">
                  <WatchlistHeartButton
                    size="small"
                    variant="overlay"
                    :active="favoriteMovieIds.has(m.id)"
                    @click.stop="toggleFavorite(m)"
                  />
                  <button class="card-cta primary" @click.stop="goToDetail(m.id)">
                    <span>选座</span>
                    <MaterialIcon class="msr" name="arrow_forward" />
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-else-if="filteredMovies.length" class="movies-list" v-loading="loading">
            <article
              v-for="(m, i) in pagedMovies"
              :key="m.id"
              class="movie-row"
              @click="goToDetail(m.id)"
            >
              <div class="row-poster" :style="{ background: cardGradient(i) }">
                <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                <span class="row-eng">{{ (m.englishTitle || m.title).slice(0, 6).toUpperCase() }}</span>
              </div>
              <div class="row-info">
                <div class="row-head">
                  <h3 class="row-title"><span v-if="isHotRanking" class="row-rank">TOP {{ i + 1 }}</span>{{ m.title }}</h3>
                  <span class="row-status">{{ statusBadge(m) }}</span>
                </div>
                <p class="row-eng-name">{{ m.englishTitle || (m.title + ' · ' + (m.releaseDate || '')) }}</p>
                <div class="row-meta">
                  <span><MaterialIcon class="msr" name="schedule" /> {{ m.duration }} 分钟</span>
                  <span><MaterialIcon class="msr" name="movie" /> {{ m.genre || '剧情' }}</span>
                  <span><MaterialIcon class="msr" name="person" /> {{ m.director || '匿名' }}</span>
                </div>
                <p class="row-desc">{{ m.description || '暂无简介' }}</p>
              </div>
              <div class="row-actions">
                <div class="row-rating">
                  <MaterialIcon class="msr" name="star" />
                  <span class="row-rating-num">{{ m.rating?.toFixed(1) || '8.5' }}</span>
                </div>
                <div class="row-ai">
                  <MaterialIcon class="msr" name="auto_awesome" />
                  <span>{{ matchScore(m.id) }}% 匹配</span>
                </div>
                <WatchlistHeartButton
                  size="small"
                  :active="favoriteMovieIds.has(m.id)"
                  @click.stop="toggleFavorite(m)"
                />
                <button class="row-cta primary" @click.stop="goToDetail(m.id)">
                  <span>¥{{ priceFor(m) }} 起 · 选座</span>
                  <MaterialIcon class="msr" name="arrow_forward" />
                </button>
              </div>
            </article>
          </div>

          <div v-if="!filteredMovies.length" class="ms-empty">
            <MaterialIcon class="msr ms-empty-ico" name="search_off" />
            <h3 class="ms-empty-title">
              {{ searchKeyword.trim() ? `未找到与“${searchKeyword.trim()}”相关的影片` : '暂无符合条件的影片' }}
            </h3>
            <p class="ms-empty-sub">换个关键词,或调整筛选条件试试</p>
            <button v-if="hasActiveFilter" class="ms-empty-btn" @click="resetFilters">
              <MaterialIcon class="msr" name="restart_alt" />
              <span>重置筛选</span>
            </button>
          </div>

          <div v-if="filteredMovies.length" class="ms-pager">
            <button
              class="pager-btn"
              :disabled="page === 1"
              @click="page = Math.max(1, page - 1)"
            >
              <MaterialIcon class="msr" name="arrow_back" />
              <span>上一页</span>
            </button>
            <div class="pager-info">
              <span class="pager-cur">{{ page }}</span>
              <span class="pager-sep">/</span>
              <span class="pager-total">{{ totalPages }}</span>
            </div>
            <button
              class="pager-btn"
              :disabled="page >= totalPages"
              @click="page = Math.min(totalPages, page + 1)"
            >
              <span>下一页</span>
              <MaterialIcon class="msr" name="arrow_forward" />
            </button>
          </div>
        </section>

        <!-- ============ AI 智能推荐 ============ -->
        <section class="ai-pick-section" v-if="!isSearching">
          <div class="aps-head">
            <div class="aps-title-block">
              <div class="aps-eyebrow">
                <MaterialIcon class="msr" name="auto_awesome" />
                <span>AI SMART PICK · 智能推荐</span>
              </div>
              <h2 class="aps-title">为你精选的影片</h2>
              <p class="aps-sub">基于你的{{ userStore.isLoggedIn ? '历史偏好与实时反馈' : '热门趋势与口碑评分' }},每日更新</p>
            </div>
            <div class="aps-tools">
              <button class="aps-refresh" @click="refreshAiPick">
                <MaterialIcon class="msr" name="refresh" />
                <span>换一批</span>
              </button>
            </div>
          </div>

          <div class="ai-pick-grid">
            <article
              v-for="(m, i) in aiPickList"
              :key="'ai-' + m.id"
              class="ai-pick-card"
              @click="goToDetail(m.id)"
            >
              <div class="apc-poster" :style="{ background: cardGradient(i + 1) }">
                <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                <div class="apc-overlay">
                  <span class="apc-badge">
                    <MaterialIcon class="msr" name="auto_awesome" />
                    <span>{{ matchScore(m.id) }}% 匹配</span>
                  </span>
                  <span class="apc-eng">{{ (m.englishTitle || m.title).toUpperCase() }}</span>
                </div>
              </div>
              <div class="apc-info">
                <h3 class="apc-title">{{ m.title }}</h3>
                <p class="apc-reason">
                  <MaterialIcon class="msr" name="tips_and_updates" />
                  <span>{{ aiReason(m, i) }}</span>
                </p>
                <div class="apc-meta">
                  <span class="apc-rate">
                    <MaterialIcon class="msr" name="star" />
                    {{ m.rating?.toFixed(1) || '8.5' }}
                  </span>
                  <span class="apc-genre">{{ m.genre || '剧情' }}</span>
                  <span class="apc-price">¥{{ priceFor(m) }} 起</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <!-- ============ AI 个性化分析 ============ -->
        <section class="ai-analysis-section" v-if="!isSearching">
          <div class="aas-head">
            <div class="aas-title-block">
              <div class="aas-eyebrow purple">
                <MaterialIcon class="msr" name="psychology" />
                <span>AI PERSONALIZATION · 个性化分析</span>
              </div>
              <h2 class="aas-title">你的观影画像</h2>
              <p class="aas-sub">{{ userStore.isLoggedIn ? `基于 ${watchCount} 次观影记录` : '登录后查看完整分析' }} · 由 CINEMA+ AI 引擎生成</p>
            </div>
            <button class="aas-action" @click="userStore.isLoggedIn ? tip('查看完整报告') : router.push('/login')">
              <span>{{ userStore.isLoggedIn ? '查看完整报告' : '立即登录' }}</span>
              <MaterialIcon class="msr" name="arrow_forward" />
            </button>
          </div>

          <div class="aas-grid">
            <!-- 1. 类型偏好 -->
            <div class="aas-card genres">
              <div class="aas-card-head">
                <div class="card-eyebrow">
                  <MaterialIcon class="msr" name="category" />
                  <span>GENRE PREFERENCE</span>
                </div>
                <span class="aas-card-tag purple">DATA</span>
              </div>
              <h3 class="aas-card-title">类型偏好</h3>
              <p class="aas-card-sub">你最常观看的电影类型分布</p>
              <div class="genre-bars">
                <div
                  v-for="(g, i) in genreStats"
                  :key="g.name"
                  class="genre-bar-row"
                >
                  <div class="genre-bar-head">
                    <span class="genre-bar-name">{{ g.name }}</span>
                    <span class="genre-bar-pct">{{ g.pct }}%</span>
                  </div>
                  <div class="genre-bar-track">
                    <div
                      class="genre-bar-fill"
                      :style="{ width: g.pct + '%', background: genreColors[i % genreColors.length] }"
                    ></div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 2. 心情画像 -->
            <div class="aas-card mood">
              <div class="aas-card-head">
                <div class="card-eyebrow">
                  <MaterialIcon class="msr" name="favorite" />
                  <span>MOOD PROFILE</span>
                </div>
                <span class="aas-card-tag pink">VIBE</span>
              </div>
              <h3 class="aas-card-title">心情画像</h3>
              <p class="aas-card-sub">你的观影情绪倾向</p>
              <div class="mood-rings">
                <div
                  v-for="m in moodStats"
                  :key="m.name"
                  class="mood-ring-item"
                >
                  <div class="mood-ring">
                    <svg viewBox="0 0 60 60" class="mood-ring-svg">
                      <circle cx="30" cy="30" r="26" class="mr-bg" />
                      <circle
                        cx="30"
                        cy="30"
                        r="26"
                        class="mr-fg"
                        :style="{ stroke: m.color, strokeDasharray: ringDash(m.pct, 26) }"
                      />
                    </svg>
                    <span class="mood-ring-pct">{{ m.pct }}</span>
                  </div>
                  <span class="mood-ring-label">{{ m.name }}</span>
                </div>
              </div>
            </div>

            <!-- 3. 导演与演员 -->
            <div class="aas-card people">
              <div class="aas-card-head">
                <div class="card-eyebrow">
                  <MaterialIcon class="msr" name="groups" />
                  <span>FAVORITES</span>
                </div>
                <span class="aas-card-tag orange">TOP</span>
              </div>
              <h3 class="aas-card-title">最爱的人</h3>
              <p class="aas-card-sub">出现频率最高的导演与演员</p>
              <div class="people-list">
                <div
                  v-for="(p, i) in peopleStats"
                  :key="p.name"
                  class="people-row"
                >
                  <div class="people-avatar" :style="{ background: peopleColors[i % peopleColors.length] }">
                    <span>{{ p.name.charAt(0) }}</span>
                  </div>
                  <div class="people-info">
                    <span class="people-name">{{ p.name }}</span>
                    <span class="people-role">{{ p.role }} · {{ p.count }} 部</span>
                  </div>
                  <div class="people-affinity">
                    <MaterialIcon class="msr" name="favorite" />
                    <span>{{ p.affinity }}%</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 4. AI 个性总结 (横跨整行) -->
            <div class="aas-card summary">
              <div class="summary-head">
                <div class="card-eyebrow light">
                  <MaterialIcon class="msr" name="auto_awesome" />
                  <span>AI INSIGHT · 智能总结</span>
                </div>
                <span class="summary-version">v 2.1 · GPT-Cinema</span>
              </div>
              <h3 class="summary-title">{{ userStore.isLoggedIn ? '你是一位深度科幻爱好者' : '开启你的观影分析' }}</h3>
              <p class="summary-text">
                {{ userStore.isLoggedIn
                  ? `你近期偏好 ${heroTags[0]}、${heroTags[1]} 类影片,平均观影时长 ${avgDuration} 分钟。AI 检测到你对诺兰风格的悬疑叙事有强烈共鸣,推荐你尝试本月新上映的《信条 II》。你的观影时间多集中在周末晚场,本周已为你预留 ${reservedSeats} 个IMAX 黄金座位。`
                  : '登录后,CINEMA+ AI 引擎将根据你的观影记录,生成专属于你的电影偏好画像,精准推荐你可能喜欢的影片,并提前预留心仪场次的黄金座位。'
                }}
              </p>
              <div class="summary-tags">
                <span class="summary-tag" v-for="t in summaryTags" :key="t">#{{ t }}</span>
              </div>
              <div class="summary-actions">
                <button class="summary-cta primary" @click="openAiAssistant">
                  <MaterialIcon class="msr" name="chat" />
                  <span>与 AI 对话</span>
                </button>
                <button class="summary-cta ghost" @click="tip('完整报告')">
                  <span>下载报告</span>
                  <MaterialIcon class="msr" name="download" />
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- ============ FOOTER ============ -->
        <div class="page-foot">
          <span>© 2026 CINEMA+ · 优质观影平台</span>
          <span class="foot-links">
            <span>用户协议</span>
            <span class="foot-sep">·</span>
            <span>隐私政策</span>
            <span class="foot-sep">·</span>
            <span>客服中心</span>
          </span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { movieApi } from '@/api/movie'
import { recommendApi } from '@/api/recommend'
import type { Movie, MovieInsight } from '@/types'
import AppSidebar from '@/components/AppSidebar.vue'
import WatchlistHeartButton from '@/components/WatchlistHeartButton.vue'
import { useLocation } from '@/composables/useLocation'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { location, ensure: ensureLocation } = useLocation()

const searchKeyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const loading = ref(false)
const statusFilter = ref<0 | 1 | 2>(route.query.ranking === 'hot' ? 1 : (route.query.ranking === 'upcoming' ? 2 : 0))
const activeGenre = ref('全部')
const sortMode = ref<'hot' | 'rating' | 'release' | 'price'>(route.query.ranking === 'upcoming' ? 'release' : 'hot')
const viewMode = ref<'grid' | 'list'>('grid')
const page = ref(1)
const pageSize = 12
const aiPickSeed = ref(0)
const favoriteMovieIds = ref(new Set<number>())
const personalizedRecommendations = ref<Movie[]>([])
const heroInsight = ref<MovieInsight | null>(null)
const insightLoading = ref(false)
const insightError = ref(false)
let insightRequestId = 0
let insightTimer: ReturnType<typeof setInterval> | undefined

const demoMovies: Movie[] = [
  { id: 1001, title: '星际穿越', englishTitle: 'Interstellar', director: '克里斯托弗·诺兰', actors: '马修·麦康纳, 安妮·海瑟薇', genre: '科幻', duration: 169, releaseDate: '2026-05-21', description: '一支宇航员小队穿越虫洞,为人类寻找新的家园。在跨越时空的旅途中,他们面对着前所未有的抉择与情感冲击。', rating: 9.4, status: 1 },
  { id: 1002, title: '沙丘 第二部', englishTitle: 'Dune Part Two', director: '丹尼斯·维伦纽瓦', actors: '提莫西·查拉梅, 赞达亚', genre: '科幻', duration: 166, releaseDate: '2026-05-15', description: '保罗·厄崔迪联合弗瑞曼人,对抗那些毁灭他家族的人。一段史诗般的复仇与觉醒之旅。', rating: 9.1, status: 1 },
  { id: 1003, title: '奥本海默', englishTitle: 'Oppenheimer', director: '克里斯托弗·诺兰', actors: '基里安·墨菲, 艾米莉·布朗特', genre: '传记', duration: 180, releaseDate: '2026-05-10', description: '原子弹之父奥本海默的故事。一部关于天才、责任与道德困境的史诗作品。', rating: 9.3, status: 1 },
  { id: 1004, title: '蜘蛛侠:平行宇宙', englishTitle: 'Spider-Verse', director: '华金·多斯·桑托斯', actors: '沙梅克·摩尔, 海莉·斯坦菲尔德', genre: '动画', duration: 140, releaseDate: '2026-04-28', description: '迈尔斯·莫拉莱斯穿越多元宇宙的冒险。视觉风暴与情感共振的完美结合。', rating: 9.0, status: 1 },
  { id: 1005, title: '宇宙探索编辑部', englishTitle: 'Journey to the West', director: '孔大山', actors: '杨皓宇, 艾丽娅', genre: '剧情', duration: 118, releaseDate: '2026-05-01', description: '一群追寻外星文明的人,在路上发现了人生意义。一部令人捧腹又催人泪下的奇幻公路片。', rating: 8.8, status: 1 },
  { id: 1006, title: '银翼杀手 2049', englishTitle: 'Blade Runner 2049', director: '丹尼斯·维伦纽瓦', actors: '瑞恩·高斯林, 哈里森·福特', genre: '科幻', duration: 163, releaseDate: '2026-04-20', description: 'K 在追查一个长埋的秘密时,他必须找到一位失踪三十年的传奇前任。', rating: 8.9, status: 1 },
  { id: 1007, title: '盗梦空间', englishTitle: 'Inception', director: '克里斯托弗·诺兰', actors: '莱昂纳多·迪卡普里奥', genre: '科幻', duration: 148, releaseDate: '2026-04-15', description: '一名身手不凡的盗梦者必须完成一次"种梦"任务来换回与家人的团聚。', rating: 9.2, status: 1 },
  { id: 1008, title: '霸王别姬', englishTitle: 'Farewell My Concubine', director: '陈凯歌', actors: '张国荣, 张丰毅', genre: '剧情', duration: 171, releaseDate: '2026-04-10', description: '两位京剧名伶半个世纪的人生悲歌。一部跨越时代的华语经典。', rating: 9.6, status: 1 },
  { id: 2001, title: '阿凡达 3', englishTitle: 'Avatar 3', director: '詹姆斯·卡梅隆', actors: '萨姆·沃辛顿', genre: '科幻', duration: 192, releaseDate: '2026-06-18', description: '潘多拉星球的全新故事。视觉奇观再度升级。', rating: 0, status: 2 },
  { id: 2002, title: '哥斯拉大战金刚 2', englishTitle: 'Godzilla x Kong 2', director: '亚当·温加德', actors: '丽贝卡·豪尔', genre: '动作', duration: 115, releaseDate: '2026-06-04', description: '两大泰坦再度联手对抗未知威胁。爽快与震撼并存。', rating: 0, status: 2 },
  { id: 2003, title: '寄生兽:灰色部队', englishTitle: 'Parasyte: The Grey', director: '延尚昊', actors: '全昭旻', genre: '惊悚', duration: 130, releaseDate: '2026-06-12', description: '寄生生物入侵后的人类生存。紧张烧脑的科幻惊悚。', rating: 0, status: 2 },
  { id: 2004, title: '疯狂动物城 2', englishTitle: 'Zootopia 2', director: '杰瑞德·布什', actors: '金妮弗·古德温', genre: '动画', duration: 108, releaseDate: '2026-06-25', description: '朱迪和尼克的全新案件。家庭与温情的最佳选择。', rating: 0, status: 2 },
  { id: 2005, title: '碟中谍 8', englishTitle: 'Mission Impossible 8', director: '克里斯托弗·麦奎里', actors: '汤姆·克鲁斯', genre: '动作', duration: 145, releaseDate: '2026-07-10', description: '伊森·亨特最危险的任务。系列收官之作。', rating: 0, status: 2 },
  { id: 2006, title: '心灵奇旅 2', englishTitle: 'Soul 2', director: '彼特·道格特', actors: '杰米·福克斯', genre: '动画', duration: 100, releaseDate: '2026-07-04', description: '关于生命意义的全新冒险。', rating: 0, status: 2 }
]

const movies = ref<Movie[]>([...demoMovies])

const statusTabs = [
  { value: 0 as const, label: '全部影片', icon: 'movie', badge: '' },
  { value: 1 as const, label: '正在热映', icon: 'play_circle', badge: 'HOT' },
  { value: 2 as const, label: '即将上映', icon: 'calendar_month', badge: '' }
]

const genres = ['全部', '科幻', '剧情', '动作', '动画', '惊悚', '传记', '喜剧']

const sortLabels: Record<string, string> = {
  hot: '热度优先',
  rating: '评分最高',
  release: '上映时间',
  price: '价格最低'
}

const heroTags = ['科幻', '诺兰', 'IMAX', '高分剧情', '太空叙事']

const genreColors = ['#FF8400', '#A855F7', '#0EA5E9', '#22C55E', '#EC4899', '#F59E0B', '#3B82F6', '#EF4444']

const peopleColors = [
  'linear-gradient(135deg, #FF8400 0%, #FF6B00 100%)',
  'linear-gradient(135deg, #A855F7 0%, #7C3AED 100%)',
  'linear-gradient(135deg, #0EA5E9 0%, #0369A1 100%)',
  'linear-gradient(135deg, #22C55E 0%, #15803D 100%)'
]

const heroMovie = computed(() => movies.value.find(m => m.status === 1) || movies.value[0])
const isHotRanking = computed(() => route.query.ranking === 'hot')
const isUpcomingListing = computed(() => route.query.ranking === 'upcoming')
const heatScore = (m: Movie) => Math.round((m.rating || 0) * 1000 + ((m.id * 97) % 300))

const filteredMovies = computed(() => {
  let list = movies.value
  if (statusFilter.value !== 0) {
    list = list.filter(m => m.status === statusFilter.value)
  }
  if (activeGenre.value !== '全部') {
    list = list.filter(m => (m.genre || '').includes(activeGenre.value))
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(m =>
      m.title.toLowerCase().includes(kw) ||
      (m.englishTitle || '').toLowerCase().includes(kw) ||
      (m.director || '').toLowerCase().includes(kw) ||
      (m.actors || '').toLowerCase().includes(kw)
    )
  }
  const sorted = [...list]
  if (sortMode.value === 'hot') sorted.sort((a, b) => heatScore(b) - heatScore(a))
  else if (sortMode.value === 'rating') sorted.sort((a, b) => (b.rating || 0) - (a.rating || 0))
  else if (sortMode.value === 'release') sorted.sort((a, b) => (b.releaseDate || '').localeCompare(a.releaseDate || ''))
  else if (sortMode.value === 'price') sorted.sort((a, b) => priceFor(a) - priceFor(b))
  return isHotRanking.value ? sorted.slice(0, 10) : sorted
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredMovies.value.length / pageSize)))
const pagedMovies = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredMovies.value.slice(start, start + pageSize)
})

const statusLabel = computed(() => statusTabs.find(t => t.value === statusFilter.value)?.label || '全部影片')
const sortLabel = computed(() => sortLabels[sortMode.value])
const avgRating = computed(() => {
  const rated = filteredMovies.value.filter(m => (m.rating || 0) > 0)
  if (rated.length === 0) return '—'
  const sum = rated.reduce((s, m) => s + (m.rating || 0), 0)
  return (sum / rated.length).toFixed(1)
})

const aiPickList = computed(() => {
  const source = personalizedRecommendations.value.length
    ? personalizedRecommendations.value
    : [...movies.value].sort((a, b) => matchScore(b.id) - matchScore(a.id))
  const count = Math.min(4, source.length)
  if (!count) return []
  const offset = aiPickSeed.value % source.length
  return Array.from({ length: count }, (_, index) => source[(offset + index) % source.length])
})

const aiReasons = [
  '与你最近观看的《奥本海默》风格高度相似',
  '同类型影片中,这部最契合你的偏好',
  '影评人评分 9.0+,你常关注的科幻佳作',
  '本周热度飙升 +42%,你的好友也在看',
  '导演的代表作之一,与你历史观影 86% 匹配',
  'IMAX 高分场次仅剩 3 张,黄金时段'
]
const aiReason = (movie: Movie, i: number) =>
  movie.recommendationReason || aiReasons[(aiPickSeed.value + i) % aiReasons.length]

const genreStats = [
  { name: '科幻', pct: 38 },
  { name: '剧情', pct: 27 },
  { name: '动作', pct: 18 },
  { name: '动画', pct: 10 },
  { name: '其他', pct: 7 }
]

const moodStats = [
  { name: '深度思考', pct: 78, color: '#A855F7' },
  { name: '热血燃情', pct: 52, color: '#FF8400' },
  { name: '温馨治愈', pct: 41, color: '#22C55E' },
  { name: '紧张刺激', pct: 64, color: '#EC4899' }
]

const peopleStats = [
  { name: '克里斯托弗·诺兰', role: '导演', count: 7, affinity: 96 },
  { name: '基里安·墨菲', role: '演员', count: 5, affinity: 89 },
  { name: '丹尼斯·维伦纽瓦', role: '导演', count: 4, affinity: 85 },
  { name: '马修·麦康纳', role: '演员', count: 3, affinity: 81 }
]

const summaryTags = ['硬核科幻迷', '诺兰拥趸', 'IMAX 体验派', '深夜场常客', '高分剧情控']

const watchCount = computed(() => userStore.isLoggedIn ? 47 : 0)
const avgDuration = computed(() => userStore.isLoggedIn ? 142 : 0)
const reservedSeats = computed(() => userStore.isLoggedIn ? 3 : 0)

const recommendationByMovieId = computed(() =>
  new Map(personalizedRecommendations.value.map(movie => [movie.id, movie]))
)

const heroRecommendation = computed(() => {
  const id = heroMovie.value?.id
  return id == null ? undefined : recommendationByMovieId.value.get(id)
})

const heroMatchLabel = computed(() => heroRecommendation.value ? 'AI 匹配度' : 'AI 推荐指数')
const heroMatchSub = computed(() => heroRecommendation.value?.recommendationReason || '综合实时热度、口碑与场次供给')

const formatCompactCount = (value: number) => {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`
  return new Intl.NumberFormat('zh-CN').format(value)
}

const heroBoxOffice = computed(() => {
  const amount = Number(heroInsight.value?.todayBoxOffice || 0)
  if (amount >= 10000) return `¥${(amount / 10000).toFixed(amount >= 100000 ? 0 : 1)}万`
  return `¥${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(amount)}`
})
const heroViewerCount = computed(() => formatCompactCount(heroInsight.value?.todayViewerCount || 0))
const heroOccupancy = computed(() => `${(heroInsight.value?.occupancyRate || 0).toFixed(1)}%`)
const heroTrend = computed(() => heroInsight.value?.trendPercent || 0)
const trendDirection = computed(() => heroTrend.value > 0 ? 'up' : (heroTrend.value < 0 ? 'down' : 'flat'))
const trendIcon = computed(() => heroTrend.value > 0 ? 'trending_up' : (heroTrend.value < 0 ? 'trending_down' : 'trending_flat'))
const trendText = computed(() => heroTrend.value > 0 ? `+${heroTrend.value}%` : `${heroTrend.value}%`)

const sparkValues = computed(() => {
  const source = heroInsight.value?.trendPoints?.length ? heroInsight.value.trendPoints : [0, 0, 0, 0, 0, 0, 0]
  const min = Math.min(...source)
  const max = Math.max(...source)
  if (max === min) return source.map(() => 20)
  return source.map(value => 5 + ((value - min) / (max - min)) * 30)
})
const sparkPoints = computed(() =>
  sparkValues.value.map((v, i) => `${(i / Math.max(1, sparkValues.value.length - 1)) * 200},${40 - v}`).join(' ')
)
const sparkAreaPoints = computed(() => {
  const pts = sparkValues.value.map((v, i) => `${(i / Math.max(1, sparkValues.value.length - 1)) * 200},${40 - v}`)
  return `0,40 ${pts.join(' ')} 200,40`
})

const insightUpdatedLabel = computed(() => {
  if (insightLoading.value && !heroInsight.value) return '正在获取实时数据…'
  if (insightError.value && !heroInsight.value) return '实时数据暂不可用'
  if (!heroInsight.value?.generatedAt) return '等待实时数据'
  const time = new Date(heroInsight.value.generatedAt)
  if (Number.isNaN(time.getTime())) return '刚刚更新'
  return `${time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })} 更新 · 每 60 秒刷新`
})

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd} ${week}`
})

const cardGradients = [
  'linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%)',
  'linear-gradient(200deg, #5c2a1a 0%, #2a0f08 100%)',
  'linear-gradient(200deg, #5c1a4a 0%, #2a0820 100%)',
  'linear-gradient(200deg, #5c5a1a 0%, #2a280a 100%)',
  'linear-gradient(200deg, #FF6B9D 0%, #8B2F5C 100%)',
  'linear-gradient(200deg, #1a5c4a 0%, #082a20 100%)',
  'linear-gradient(200deg, #3a1a5c 0%, #1a0830 100%)',
  'linear-gradient(200deg, #5c3a1a 0%, #2a1c08 100%)'
]
const cardGradient = (i: number) => cardGradients[Math.abs(i) % cardGradients.length]

const priceFor = (m: Movie) => 38 + (m.id % 7) * 6
const matchScore = (id: number) => {
  const recommended = recommendationByMovieId.value.get(id)
  if (recommended?.matchScore != null) return Math.round(recommended.matchScore)
  const movie = movies.value.find(item => item.id === id)
  const ratingScore = Math.round((movie?.rating || 7.5) * 10)
  return Math.max(60, Math.min(96, ratingScore))
}

const statusBadge = (m: Movie) => {
  if (m.status === 1) return 'NOW SHOWING'
  if (m.status === 2) return 'UPCOMING'
  return 'HOT'
}

const ringDash = (pct: number, r = 24) => {
  const c = 2 * Math.PI * r
  return `${(pct / 100) * c} ${c}`
}

const cycleSort = () => {
  const modes: ('hot' | 'rating' | 'release' | 'price')[] = ['hot', 'rating', 'release', 'price']
  const idx = modes.indexOf(sortMode.value)
  sortMode.value = modes[(idx + 1) % modes.length]
  if ((isHotRanking.value && sortMode.value !== 'hot') ||
      (isUpcomingListing.value && sortMode.value !== 'release')) {
    router.replace({ path: '/movies' })
  }
}

const setStatusFilter = (v: 0 | 1 | 2) => {
  statusFilter.value = v
  page.value = 1
  if ((isHotRanking.value && v !== 1) || (isUpcomingListing.value && v !== 2)) {
    router.replace({ path: '/movies' })
  }
}

const refreshAiPick = () => {
  aiPickSeed.value += 1
  ElMessage.success('已为你换一批 AI 推荐')
}

const onSearch = () => {
  page.value = 1
  const kw = searchKeyword.value.trim()
  const current = typeof route.query.q === 'string' ? route.query.q : ''
  if (kw === current) return
  router.replace({ path: '/movies', query: kw ? { q: kw } : {} })
}

const hasActiveFilter = computed(() =>
  !!searchKeyword.value.trim() || statusFilter.value !== 0 || activeGenre.value !== '全部'
)

const isSearching = computed(() => !!searchKeyword.value.trim())

const resetFilters = () => {
  searchKeyword.value = ''
  statusFilter.value = 0
  activeGenre.value = '全部'
  page.value = 1
  if (route.query.q || route.query.ranking) router.replace({ path: '/movies', query: {} })
}

watch(
  () => [route.query.q, route.query.ranking] as const,
  ([q, ranking]) => {
    const next = typeof q === 'string' ? q : ''
    if (next !== searchKeyword.value) {
      searchKeyword.value = next
      page.value = 1
    }
    if (ranking === 'hot') {
      searchKeyword.value = ''
      statusFilter.value = 1
      sortMode.value = 'hot'
      activeGenre.value = '全部'
      page.value = 1
    } else if (ranking === 'upcoming') {
      searchKeyword.value = ''
      statusFilter.value = 2
      sortMode.value = 'release'
      activeGenre.value = '全部'
      page.value = 1
    }
  }
)

const goToDetail = (id: number) => {
  router.push(`/movie/${id}`)
}

const openAiAssistant = () => {
  window.dispatchEvent(new Event('open-movie-assistant'))
}

const loadFavorites = async () => {
  if (!userStore.isLoggedIn) return
  const list = await recommendApi.favorites().catch(() => [] as Movie[])
  favoriteMovieIds.value = new Set(list.map(movie => movie.id))
}

const loadRecommendations = async () => {
  if (!userStore.isLoggedIn) {
    personalizedRecommendations.value = []
    return
  }
  personalizedRecommendations.value = await recommendApi.personal({
    limit: 50,
    latitude: location.value.lat,
    longitude: location.value.lng
  }).catch(() => [] as Movie[])
}

const loadHeroInsight = async () => {
  const movieId = heroMovie.value?.id
  if (!movieId) return false
  const requestId = ++insightRequestId
  insightLoading.value = true
  try {
    const insight = await movieApi.getInsight(movieId)
    if (requestId !== insightRequestId || insight.movieId !== heroMovie.value?.id) return false
    heroInsight.value = insight
    insightError.value = false
    return true
  } catch (error) {
    if (requestId === insightRequestId) insightError.value = true
    console.error('加载影片实时洞察失败:', error)
    return false
  } finally {
    if (requestId === insightRequestId) insightLoading.value = false
  }
}

const refreshInsight = async () => {
  const success = await loadHeroInsight()
  if (success) ElMessage.success('AI 实时洞察已更新')
}

const refreshWhenVisible = () => {
  if (document.visibilityState === 'visible') loadHeroInsight()
}

const toggleFavorite = async (movie: Movie) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('登录后才能加入想看')
    router.push('/login')
    return
  }
  const active = await recommendApi.toggleFavorite(movie.id)
  const next = new Set(favoriteMovieIds.value)
  active ? next.add(movie.id) : next.delete(movie.id)
  favoriteMovieIds.value = next
  ElMessage.success(active ? `已将《${movie.title}》加入想看` : `已将《${movie.title}》移出想看`)
}

const tip = (label: string) => {
  ElMessage.info(`${label} 功能敬请期待`)
}

const loadAllMovies = async () => {
  loading.value = true
  try {
    const [nowList, comingList] = await Promise.all([
      movieApi.getNowShowing().catch(() => [] as Movie[]),
      movieApi.getComingSoon().catch(() => [] as Movie[])
    ])
    const merged: Movie[] = []
    nowList.forEach(m => merged.push({ ...m, status: 1 }))
    comingList.forEach(m => merged.push({ ...m, status: 2 }))
    if (merged.length > 0) movies.value = merged
  } catch (e) {
    console.error('加载影片失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ensureLocation()
  await Promise.all([loadAllMovies(), loadFavorites()])
  await Promise.all([loadHeroInsight(), loadRecommendations()])
  insightTimer = setInterval(loadHeroInsight, 60_000)
  document.addEventListener('visibilitychange', refreshWhenVisible)
})

onBeforeUnmount(() => {
  insightRequestId += 1
  if (insightTimer) clearInterval(insightTimer)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200&display=swap');

.msr {
  font-family: 'Material Symbols Rounded';
  font-weight: normal;
  font-style: normal;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
  font-size: 20px;
  user-select: none;
}

.app {
  --primary: #FF8400;
  --primary-foreground: #111111;
  --bg: #F2F3F0;
  --card: #FFFFFF;
  --fg: #111111;
  --muted: #666666;
  --border: #CBCCC9;
  --secondary: #E7E8E5;
  --sidebar: #E7E8E5;
  --sidebar-accent: #CBCCC9;
  --sidebar-fg: #666666;
  --sidebar-accent-fg: #18181b;
  --sidebar-border: #CBCCC9;
  --radius-m: 16px;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-sans: 'Geist', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  display: flex;
  min-height: 100vh;
  background: var(--bg);
  font-family: var(--font-sans);
  color: var(--fg);
}

/* ============ SIDEBAR ============ */
.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--sidebar);
  border-right: 1px solid var(--sidebar-border);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}

.logo-block {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 90px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--sidebar-border);
  flex-shrink: 0;
}
.logo-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.logo-mark .msr { font-size: 24px; }
.logo-text { display: flex; flex-direction: column; gap: 2px; }
.brand {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  color: var(--sidebar-accent-fg);
  letter-spacing: 1.5px;
}
.brand-sub {
  font-size: 12px;
  color: var(--sidebar-fg);
}

.sidebar-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin: 16px;
  background: var(--sidebar-accent);
  border: 1px solid var(--sidebar-border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.sidebar-search:hover { border-color: var(--primary); }
.sidebar-search .search-ico { font-size: 18px; color: var(--sidebar-fg); }
.search-text { flex: 1; font-size: 14px; color: var(--sidebar-fg); }
.kbd {
  padding: 2px 6px;
  border: 1px solid var(--sidebar-border);
  border-radius: 4px;
  background: var(--sidebar);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--sidebar-fg);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
  text-decoration: none;
  color: inherit;
}
.nav-item:hover { background: rgba(0, 0, 0, 0.04); }
.nav-item .msr { font-size: 24px; color: var(--sidebar-fg); }
.nav-label {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.nav-item.active { background: var(--primary); }
.nav-item.active .msr,
.nav-item.active .nav-label { color: var(--primary-foreground); font-weight: 600; }
.nav-badge {
  padding: 3px 10px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  background: var(--sidebar-accent);
  color: var(--sidebar-fg);
}
.nav-item.active .nav-badge.badge-primary {
  background: rgba(17, 17, 17, 0.2);
  color: var(--primary-foreground);
}

.sidebar-divider {
  height: 1px;
  background: var(--sidebar-border);
  margin: 4px 0;
}

.quick-access {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 20px 14px;
}
.qa-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: var(--sidebar-fg);
}
.qa-head .msr { font-size: 16px; }
.qa-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}
.qa-item:hover { background: rgba(0, 0, 0, 0.04); }
.qa-ico { font-size: 18px; color: var(--primary); }
.qa-label {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--sidebar-accent-fg);
}
.qa-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--primary);
}

.sidebar-spacer { flex: 1; }

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-top: 1px solid var(--sidebar-border);
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: linear-gradient(135deg, #FF8400 0%, #FF3F33 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.guest-avatar {
  background: var(--sidebar-accent);
  color: var(--sidebar-fg);
}
.guest-avatar .msr { font-size: 20px; }
.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
  transition: opacity 0.15s;
}
.user-info:hover { opacity: 0.8; }
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sidebar-accent-fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-tier {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--sidebar-fg);
}
.more-btn {
  font-size: 18px;
  color: var(--sidebar-fg);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}
.more-btn:hover { background: var(--sidebar-accent); }
.footer-login {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.footer-login:hover { opacity: 0.88; }

/* ============ MAIN AREA ============ */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 32px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(8px);
}
.tb-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.tb-pill:hover { border-color: var(--primary); }
.tb-pill .msr { font-size: 16px; color: var(--muted); }
.tb-pill .primary { color: var(--primary); }
.tb-pill-caret { font-size: 14px !important; }
.tb-pill-text { font-size: 13px; font-weight: 500; }
.tb-loc, .tb-date {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.tb-loc:hover, .tb-date:hover { border-color: var(--primary); }
.tb-loc .msr, .tb-date .msr { font-size: 16px; color: var(--muted); }
.loc-name, .date-text { font-size: 13px; font-weight: 500; color: var(--fg); }
.loc-chev { color: var(--muted) !important; font-size: 14px !important; }

.tb-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.15s;
}
.tb-search:focus-within { border-color: var(--primary); }
.tb-search .msr { font-size: 18px; color: var(--muted); }
.tb-search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 14px;
  color: var(--fg);
}
.tb-search input::placeholder { color: var(--muted); }

.tb-icon-group { display: flex; gap: 8px; }
.tb-icon {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--card);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  position: relative;
  transition: border-color 0.15s;
}
.tb-icon:hover { border-color: var(--primary); }
.tb-icon .msr { font-size: 18px; color: var(--fg); }
.tb-dot {
  position: absolute;
  top: 7px;
  right: 9px;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
  border: 2px solid var(--card);
  box-sizing: content-box;
}
.tb-cta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
  transition: transform 0.15s, box-shadow 0.2s;
}
.tb-cta:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35); }
.tb-cta .msr { font-size: 16px; }

.content {
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 28px 32px;
}

/* ============ MOVIES HERO ============ */
.m-hero {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 28px;
}
.m-hero-main {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 28px;
}
.m-hero-poster {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  aspect-ratio: 2 / 3;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
}
.m-hero-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.m-hero-poster-overlay {
  position: absolute;
  inset: 0;
  padding: 18px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.75) 100%);
  color: #fff;
}
.m-poster-eng {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(255,255,255,0.7);
  margin-bottom: 4px;
}
.m-poster-title { font-size: 22px; font-weight: 800; line-height: 1.2; }

.m-hero-info { display: flex; flex-direction: column; gap: 14px; }
.m-hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(255, 132, 0, 0.1);
  color: var(--primary);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  width: fit-content;
}
.m-hero-eyebrow .msr { font-size: 14px; }
.m-hero-title {
  font-size: 44px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.05;
  margin: 0;
}
.m-hero-eng {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--muted);
  margin: -4px 0 0 0;
}
.m-hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--muted);
}
.m-hero-rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(255, 132, 0, 0.12);
  color: var(--primary);
  border-radius: 6px;
  font-weight: 700;
  font-size: 13px;
}
.m-hero-rating .msr { font-size: 14px; }
.m-hero-dot { opacity: 0.4; }
.m-hero-desc {
  font-size: 14px;
  line-height: 1.6;
  color: var(--muted);
  margin: 0;
}
.m-hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.m-hero-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  background: var(--secondary);
  border-radius: 999px;
  color: var(--muted);
  font-weight: 500;
}
.m-hero-actions { display: flex; gap: 10px; margin-top: auto; flex-wrap: wrap; }
.m-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.m-cta.primary {
  background: var(--primary);
  color: #fff;
}
.m-cta.primary:hover { background: #FF6B00; }
.m-cta.ghost {
  background: var(--secondary);
  color: var(--fg);
}
.m-cta.icon {
  padding: 12px;
  background: var(--secondary);
  color: var(--fg);
}
.m-cta.icon.active { color: #fff; background: #ec4899; }
.m-cta .msr { font-size: 18px; }

/* HERO SIDE */
.m-hero-side {
  background: linear-gradient(180deg, #111 0%, #1a1a1a 100%);
  border-radius: 16px;
  padding: 20px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.side-eyebrow {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.7);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.side-icon {
  color: var(--primary);
  font-size: 16px;
}
.side-live {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  color: #86efac;
}
.side-live.loading { color: #fdba74; }
.side-live.error { color: rgba(255,255,255,0.5); }
.side-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 16%, transparent);
}
.side-live:not(.error) .side-live-dot { animation: insight-pulse 1.8s ease-out infinite; }
.side-refresh {
  width: 24px;
  height: 24px;
  margin-left: 2px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 50%;
  background: rgba(255,255,255,0.06);
  color: inherit;
  cursor: pointer;
}
.side-refresh:hover:not(:disabled) { background: rgba(255,255,255,0.12); }
.side-refresh:disabled { cursor: wait; opacity: 0.55; }
.side-refresh .msr { font-size: 13px; }
.side-live.loading .side-refresh .msr { animation: insight-spin 0.8s linear infinite; }
@keyframes insight-pulse {
  0% { box-shadow: 0 0 0 0 color-mix(in srgb, currentColor 34%, transparent); }
  70%, 100% { box-shadow: 0 0 0 6px transparent; }
}
@keyframes insight-spin { to { transform: rotate(360deg); } }
.side-match {
  display: flex;
  align-items: center;
  gap: 16px;
}
.match-ring {
  position: relative;
  width: 64px;
  height: 64px;
}
.ring-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-bg { fill: none; stroke: rgba(255,255,255,0.1); stroke-width: 5; }
.ring-fg { fill: none; stroke: var(--primary); stroke-width: 5; stroke-linecap: round; transition: stroke-dasharray 0.6s; }
.match-pct {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 800;
  color: #fff;
}
.match-text { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.match-label { font-size: 14px; font-weight: 700; }
.match-sub {
  font-size: 11px;
  line-height: 1.45;
  color: rgba(255,255,255,0.6);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.side-divider { height: 1px; background: rgba(255,255,255,0.1); }
.side-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.side-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-num {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 800;
  color: var(--primary);
}
.stat-label {
  font-size: 10px;
  color: rgba(255,255,255,0.5);
  letter-spacing: 0.02em;
}
.side-trend { display: flex; flex-direction: column; gap: 8px; }
.trend-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.trend-label { font-size: 12px; color: rgba(255,255,255,0.7); }
.trend-up {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
}
.trend-up.up { color: #22C55E; }
.trend-up.down { color: #fb7185; }
.trend-up .msr { font-size: 14px; }
.trend-spark { width: 100%; height: 40px; }
.insight-updated {
  margin-top: -4px;
  font-family: var(--font-mono);
  font-size: 9px;
  color: rgba(255,255,255,0.38);
}

/* ============ FILTER BAR ============ */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 16px 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  flex-wrap: wrap;
}
.fb-tabs { display: flex; gap: 4px; padding: 4px; background: var(--secondary); border-radius: 999px; }
.fb-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}
.fb-tab.active {
  background: var(--card);
  color: var(--fg);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.fb-tab .msr { font-size: 16px; }
.fb-tab-badge {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  background: #FEE2E2;
  color: #DC2626;
  border-radius: 3px;
}

.fb-genres { display: flex; gap: 6px; flex-wrap: wrap; flex: 1; }
.fb-genre {
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}
.fb-genre:hover { border-color: var(--primary); color: var(--primary); }
.fb-genre.active {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
}

.fb-tools { display: flex; gap: 10px; align-items: center; }
.fb-sort {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
}
.fb-sort .msr { font-size: 16px; color: var(--muted); }
.fb-view { display: flex; padding: 3px; background: var(--secondary); border-radius: 8px; }
.fb-view-btn {
  padding: 6px 10px;
  background: transparent;
  border: none;
  border-radius: 5px;
  color: var(--muted);
  cursor: pointer;
}
.fb-view-btn .msr { font-size: 16px; }
.fb-view-btn.active {
  background: var(--card);
  color: var(--fg);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

/* ============ MOVIES SECTION ============ */
.movies-section { display: flex; flex-direction: column; gap: 18px; }
.ms-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ms-title-block { display: flex; flex-direction: column; gap: 4px; }
.ms-title { font-size: 28px; font-weight: 800; letter-spacing: -0.02em; margin: 0; }
.ms-sub { font-size: 13px; color: var(--muted); margin: 0; }
.ms-clear {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 10px;
  padding: 3px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
  vertical-align: middle;
}
.ms-clear:hover { border-color: var(--primary); color: var(--primary); }
.ms-clear .msr { font-size: 14px; }
.ms-summary { display: flex; gap: 8px; flex-wrap: wrap; }
.ms-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
}
.ms-chip .msr { font-size: 14px; color: var(--primary); }
.ms-chip.ai {
  background: linear-gradient(135deg, #F3E8FF, #FFE4F1);
  border-color: transparent;
  color: #7C3AED;
}
.ms-chip.ai .msr { color: #A855F7; }

.movies-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.movie-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}
.movie-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
  border-color: var(--primary);
}
.row-rank {
  display: inline-flex;
  align-items: center;
  margin-right: 9px;
  padding: 3px 7px;
  border-radius: 999px;
  color: #111;
  background: var(--primary);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 800;
  vertical-align: middle;
}
.card-poster {
  position: relative;
  aspect-ratio: 3 / 4;
  overflow: hidden;
}
.card-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-top {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  justify-content: space-between;
}
.card-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(0,0,0,0.6);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.06em;
  backdrop-filter: blur(8px);
}
.card-badge.primary { background: var(--primary); }
.dot-now {
  width: 6px;
  height: 6px;
  background: #22C55E;
  border-radius: 50%;
}
.card-badge.primary .dot-now { background: #fff; }
.card-rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px 8px;
  background: rgba(255,255,255,0.95);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--fg);
}
.card-rating .msr { font-size: 12px; color: var(--primary); }
.card-ai {
  position: absolute;
  z-index: 2;
  bottom: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(168, 85, 247, 0.9);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  backdrop-filter: blur(8px);
}
.card-ai .msr { font-size: 12px; }
.card-title-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  min-height: 58px;
  padding: 12px 14px 0;
  text-align: center;
}
.card-title-below {
  width: 100%;
  margin: 0;
  color: var(--fg);
  font-size: 16px;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-eng-below {
  width: 100%;
  margin: 0;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 14px;
  gap: 12px;
}
.card-meta { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.card-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.card-genre-row { display: flex; align-items: center; gap: 4px; }
.genre-ico { font-size: 14px !important; color: #F59E0B; }
.card-genre {
  font-size: 11px;
  color: var(--muted);
  font-weight: 500;
}
.card-price-row { display: flex; align-items: baseline; gap: 1px; color: var(--primary); }
.price-cur { font-size: 11px; font-weight: 700; }
.price-num { font-family: var(--font-mono); font-size: 20px; font-weight: 800; }
.price-unit { font-size: 10px; color: var(--muted); margin-left: 2px; }
.card-cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border: none;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  background: var(--fg);
  color: #fff;
  transition: all 0.15s;
}
.card-cta.primary { background: var(--primary); }
.card-cta:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(255,132,0,0.4); }
.card-cta .msr { font-size: 14px; }

/* LIST VIEW */
.movies-list { display: flex; flex-direction: column; gap: 12px; }
.movie-row {
  display: grid;
  grid-template-columns: 120px 1fr 220px;
  gap: 18px;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
}
.movie-row:hover {
  border-color: var(--primary);
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}
.row-poster {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  padding: 10px;
}
.row-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.row-eng {
  position: relative;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: rgba(255,255,255,0.8);
}
.row-info { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.row-head { display: flex; align-items: center; gap: 10px; }
.row-title { font-size: 20px; font-weight: 800; margin: 0; }
.row-status {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 3px 8px;
  background: var(--secondary);
  border-radius: 999px;
  color: var(--muted);
  letter-spacing: 0.06em;
}
.row-eng-name {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--muted);
  margin: 0;
  letter-spacing: 0.05em;
}
.row-meta { display: flex; gap: 12px; font-size: 12px; color: var(--muted); flex-wrap: wrap; }
.row-meta span { display: inline-flex; align-items: center; gap: 4px; }
.row-meta .msr { font-size: 14px; }
.row-desc {
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
  margin: 4px 0 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.row-actions { display: flex; flex-direction: column; gap: 8px; align-items: flex-end; justify-content: center; }
.row-rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: rgba(255, 132, 0, 0.12);
  color: var(--primary);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-weight: 700;
  font-size: 13px;
}
.row-rating .msr { font-size: 14px; }
.row-ai {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  color: #A855F7;
}
.row-ai .msr { font-size: 14px; }
.row-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  background: var(--primary);
  color: #fff;
}
.row-cta .msr { font-size: 16px; }

/* PAGER */
.ms-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 14px;
}
.pager-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.pager-btn:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
}
.pager-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.pager-btn .msr { font-size: 16px; }
.pager-info {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-family: var(--font-mono);
}
.pager-cur { font-size: 20px; font-weight: 800; color: var(--primary); }
.pager-sep { color: var(--muted); }
.pager-total { font-size: 14px; font-weight: 600; color: var(--muted); }

/* EMPTY STATE */
.ms-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  background: var(--card);
  border: 1.5px dashed var(--border);
  border-radius: 16px;
  text-align: center;
}
.ms-empty-ico { font-size: 56px; color: var(--muted); opacity: 0.6; }
.ms-empty-title { font-size: 18px; font-weight: 700; color: var(--fg); margin: 4px 0 0 0; }
.ms-empty-sub { font-size: 13px; color: var(--muted); margin: 0; }
.ms-empty-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 10px 20px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.ms-empty-btn:hover { background: #FF6B00; transform: translateY(-1px); }
.ms-empty-btn .msr { font-size: 16px; }

/* ============ AI 智能推荐 ============ */
.ai-pick-section {
  background: linear-gradient(135deg, #FFF8F0 0%, #FFF 100%);
  border: 1px solid #FFE4CC;
  border-radius: 24px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.aps-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.aps-title-block { display: flex; flex-direction: column; gap: 6px; }
.aps-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--primary);
  color: #fff;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  width: fit-content;
}
.aps-eyebrow .msr { font-size: 14px; }
.aps-title { font-size: 26px; font-weight: 800; letter-spacing: -0.02em; margin: 0; }
.aps-sub { font-size: 13px; color: var(--muted); margin: 0; }
.aps-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  cursor: pointer;
}
.aps-refresh:hover { border-color: var(--primary); color: var(--primary); }
.aps-refresh .msr { font-size: 16px; }

.ai-pick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.ai-pick-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}
.ai-pick-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(0,0,0,0.1);
}
.apc-poster {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
}
.apc-poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.apc-overlay {
  position: absolute;
  inset: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: linear-gradient(180deg, rgba(0,0,0,0.4) 0%, transparent 40%, rgba(0,0,0,0.7) 100%);
}
.apc-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(168, 85, 247, 0.95);
  color: #fff;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  width: fit-content;
  backdrop-filter: blur(8px);
}
.apc-badge .msr { font-size: 12px; }
.apc-eng {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: rgba(255,255,255,0.85);
}

.apc-info {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.apc-title { font-size: 16px; font-weight: 800; margin: 0; }
.apc-reason {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.4;
  margin: 0;
}
.apc-reason .msr { font-size: 14px; color: #A855F7; flex-shrink: 0; margin-top: 1px; }
.apc-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 6px;
  border-top: 1px solid var(--secondary);
}
.apc-rate {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--primary);
}
.apc-rate .msr { font-size: 13px; }
.apc-genre {
  font-size: 11px;
  color: var(--muted);
}
.apc-price {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--fg);
  margin-left: auto;
}

/* ============ AI 个性化分析 ============ */
.ai-analysis-section {
  background: linear-gradient(135deg, #1a1129 0%, #2a1a4a 100%);
  border-radius: 24px;
  padding: 32px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 24px;
  position: relative;
  overflow: hidden;
}
.ai-analysis-section::before {
  content: '';
  position: absolute;
  top: -100px;
  right: -100px;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.4) 0%, transparent 60%);
  pointer-events: none;
}
.ai-analysis-section::after {
  content: '';
  position: absolute;
  bottom: -120px;
  left: 30%;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(255, 132, 0, 0.25) 0%, transparent 65%);
  pointer-events: none;
}
.aas-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  position: relative;
}
.aas-title-block { display: flex; flex-direction: column; gap: 8px; }
.aas-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: rgba(255,255,255,0.12);
  color: #fff;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  width: fit-content;
  backdrop-filter: blur(8px);
}
.aas-eyebrow.purple { background: rgba(168, 85, 247, 0.25); border: 1px solid rgba(168, 85, 247, 0.4); }
.aas-eyebrow .msr { font-size: 14px; }
.aas-title {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0;
  color: #fff;
}
.aas-sub {
  font-size: 13px;
  color: rgba(255,255,255,0.65);
  margin: 0;
}
.aas-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 12px 22px;
  background: var(--primary);
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  cursor: pointer;
  transition: all 0.15s;
}
.aas-action:hover { background: #FF6B00; transform: translateY(-1px); }
.aas-action .msr { font-size: 16px; }

.aas-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  position: relative;
}
.aas-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 16px;
  padding: 22px;
  backdrop-filter: blur(20px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.aas-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.5);
}
.card-eyebrow.light { color: rgba(255,255,255,0.7); }
.card-eyebrow .msr { font-size: 13px; color: #A855F7; }
.card-eyebrow.light .msr { color: var(--primary); }
.aas-card-tag {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  padding: 3px 7px;
  border-radius: 4px;
  letter-spacing: 0.04em;
}
.aas-card-tag.purple { background: rgba(168, 85, 247, 0.25); color: #C084FC; }
.aas-card-tag.pink { background: rgba(236, 72, 153, 0.25); color: #F472B6; }
.aas-card-tag.orange { background: rgba(255, 132, 0, 0.25); color: #FFB672; }
.aas-card-title { font-size: 18px; font-weight: 800; margin: 0; color: #fff; }
.aas-card-sub {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  margin: 0;
}

/* genre bars */
.genre-bars { display: flex; flex-direction: column; gap: 8px; margin-top: 4px; }
.genre-bar-row { display: flex; flex-direction: column; gap: 4px; }
.genre-bar-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}
.genre-bar-name { color: rgba(255,255,255,0.85); font-weight: 500; }
.genre-bar-pct {
  font-family: var(--font-mono);
  font-weight: 700;
  color: #fff;
}
.genre-bar-track {
  height: 6px;
  background: rgba(255,255,255,0.08);
  border-radius: 3px;
  overflow: hidden;
}
.genre-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s;
}

/* mood rings */
.mood-rings {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 4px;
}
.mood-ring-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px;
  background: rgba(255,255,255,0.04);
  border-radius: 12px;
}
.mood-ring {
  position: relative;
  width: 56px;
  height: 56px;
}
.mood-ring-svg { width: 100%; height: 100%; transform: rotate(-90deg); }
.mr-bg { fill: none; stroke: rgba(255,255,255,0.1); stroke-width: 5; }
.mr-fg { fill: none; stroke-width: 5; stroke-linecap: round; transition: stroke-dasharray 0.6s; }
.mood-ring-pct {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
  color: #fff;
}
.mood-ring-label {
  font-size: 11px;
  color: rgba(255,255,255,0.7);
  font-weight: 500;
}

/* people list */
.people-list { display: flex; flex-direction: column; gap: 8px; margin-top: 4px; }
.people-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: rgba(255,255,255,0.04);
  border-radius: 10px;
}
.people-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  color: #fff;
  flex-shrink: 0;
}
.people-info { flex: 1; display: flex; flex-direction: column; line-height: 1.2; min-width: 0; }
.people-name {
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.people-role { font-size: 10px; color: rgba(255,255,255,0.5); font-family: var(--font-mono); }
.people-affinity {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: #EC4899;
}
.people-affinity .msr { font-size: 12px; }

/* summary card */
.aas-card.summary {
  grid-column: 1 / -1;
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.2) 0%, rgba(255, 132, 0, 0.15) 100%);
  border: 1px solid rgba(168, 85, 247, 0.3);
}
.summary-head { display: flex; align-items: center; justify-content: space-between; }
.summary-version {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  color: rgba(255,255,255,0.5);
  letter-spacing: 0.04em;
}
.summary-title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.01em;
  margin: 0;
  color: #fff;
}
.summary-text {
  font-size: 14px;
  line-height: 1.65;
  color: rgba(255,255,255,0.85);
  margin: 0;
}
.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}
.summary-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 4px 10px;
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 999px;
  color: #fff;
  font-weight: 600;
}
.summary-actions { display: flex; gap: 10px; margin-top: 6px; flex-wrap: wrap; }
.summary-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}
.summary-cta.primary {
  background: var(--primary);
  color: #fff;
}
.summary-cta.primary:hover { background: #FF6B00; transform: translateY(-1px); }
.summary-cta.ghost {
  background: rgba(255,255,255,0.1);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.2);
}
.summary-cta .msr { font-size: 16px; }

/* FOOTER */
.page-foot {
  margin-top: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--muted);
}
.foot-links { display: flex; gap: 8px; }
.foot-sep { opacity: 0.5; }

/* ============ RESPONSIVE ============ */
@media (max-width: 1440px) {
  .movies-grid { grid-template-columns: repeat(3, 1fr); }
  .ai-pick-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 1280px) {
  .m-hero { grid-template-columns: 1fr; }
  .m-hero-side { order: -1; }
  .m-hero-title { font-size: 36px; }
}

@media (max-width: 1024px) {
  .sidebar { display: none; }
  .movies-grid { grid-template-columns: repeat(2, 1fr); }
  .ai-pick-grid { grid-template-columns: repeat(2, 1fr); }
  .aas-grid { grid-template-columns: 1fr; }
  .m-hero-main { grid-template-columns: 1fr; }
  .m-hero-poster { max-width: 240px; margin: 0 auto; }
  .movie-row { grid-template-columns: 90px 1fr; }
  .row-actions { grid-column: 1 / -1; flex-direction: row; justify-content: space-between; align-items: center; }
}

@media (max-width: 640px) {
  .content { padding: 16px; gap: 20px; }
  .m-hero { padding: 18px; }
  .m-hero-title { font-size: 28px; }
  .movies-grid, .ai-pick-grid { grid-template-columns: 1fr 1fr; gap: 12px; }
  .top-bar { flex-wrap: wrap; padding: 14px 18px; gap: 10px; }
  .tb-search { order: 5; width: 100%; flex: 0 0 100%; }
  .ai-analysis-section { padding: 22px; }
  .aas-title, .aps-title, .ms-title { font-size: 22px; }
}
</style>
