<template>
  <div class="app">
    <!-- ============ SIDEBAR ============ -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="logo-block" @click="router.push('/home')">
        <div class="logo-mark">
          <span class="msr">movie</span>
        </div>
        <div class="logo-text">
          <span class="brand">CINEMA+</span>
          <span class="brand-sub">premium booking</span>
        </div>
      </div>

      <!-- Search -->
      <div class="sidebar-search">
        <span class="msr search-ico">search</span>
        <span class="search-text">搜索影片...</span>
        <span class="kbd">⌘K</span>
      </div>

      <!-- Nav -->
      <nav class="nav">
        <a
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: item.key === activeNav }"
          @click="onNavClick(item)"
        >
          <span class="msr">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="item.badge" class="nav-badge" :class="item.badgeClass">{{ item.badge }}</span>
        </a>
      </nav>

      <div class="sidebar-divider"></div>

      <!-- Quick Access -->
      <div class="quick-access">
        <div class="qa-head">
          <span>QUICK ACCESS</span>
          <span class="msr">expand_more</span>
        </div>
        <a class="qa-item" @click="tip('热映榜单')">
          <span class="msr qa-ico">trending_up</span>
          <span class="qa-label">热映榜单</span>
          <span class="qa-tag">TOP 10</span>
        </a>
        <a class="qa-item" @click="tip('周末特惠')">
          <span class="msr qa-ico">local_fire_department</span>
          <span class="qa-label">周末特惠</span>
          <span class="qa-tag">-30%</span>
        </a>
        <a class="qa-item" @click="tip('兑换券码')">
          <span class="msr qa-ico">redeem</span>
          <span class="qa-label">兑换券码</span>
          <span class="qa-tag">新</span>
        </a>
      </div>

      <div class="sidebar-spacer"></div>

      <!-- User Footer -->
      <div class="sidebar-footer" v-if="userStore.isLoggedIn">
        <div class="avatar" @click="router.push('/profile')">{{ avatarChar }}</div>
        <div class="user-info" @click="router.push('/profile')">
          <span class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</span>
          <span class="user-tier">GOLD · 1280 积分</span>
        </div>
        <el-dropdown trigger="click">
          <span class="msr more-btn">more_horiz</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
              <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div v-else class="sidebar-footer guest">
        <div class="avatar guest-avatar">
          <span class="msr">person</span>
        </div>
        <div class="user-info">
          <span class="user-name">访客</span>
          <span class="user-tier">登录解锁会员权益</span>
        </div>
        <button class="footer-login" @click="router.push('/login')">登录</button>
      </div>
    </aside>

    <!-- ============ MAIN AREA ============ -->
    <main class="main-area">
      <!-- Top Bar -->
      <div class="top-bar">
        <button class="tb-back" @click="router.back()">
          <span class="msr">arrow_back</span>
          <span>返回</span>
        </button>
        <div class="tb-pill">
          <span class="msr tb-pill-ico primary">location_on</span>
          <span class="tb-pill-text">上海 · 浦东新区</span>
          <span class="msr tb-pill-caret">keyboard_arrow_down</span>
        </div>
        <div class="tb-search">
          <span class="msr tb-search-ico" @click="onSearch">search</span>
          <input
            type="text"
            placeholder="搜索电影 · 影院 · 演员"
            v-model="searchKeyword"
            @keyup.enter="onSearch"
          />
        </div>
        <div class="tb-pill">
          <span class="msr">calendar_month</span>
          <span class="tb-pill-text">{{ todayLabel }}</span>
        </div>
        <div class="tb-icon-group">
          <button class="tb-icon" @click="tip('通知')">
            <span class="msr">notifications</span>
            <span class="tb-dot"></span>
          </button>
          <button class="tb-icon" @click="tip('购物车')">
            <span class="msr">shopping_cart</span>
          </button>
        </div>
        <button class="tb-cta" @click="tip('扫码取票')">
          <span class="msr">qr_code_scanner</span>
          <span>扫码取票</span>
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="page-loading">
        <div class="loading-spin">
          <span class="msr">progress_activity</span>
        </div>
      </div>

      <!-- Content Scroll -->
      <div class="content" v-else-if="movie">
        <!-- ============ HERO ============ -->
        <section class="hero">
          <div class="hero-bg-num">01</div>
          <div class="hero-inner">
            <!-- Hero Poster Card -->
            <div class="hero-poster-card" :style="{ background: heroPosterGradient }">
              <div class="hpc-top">
                <div class="hpc-num">01</div>
                <div class="hpc-tag">
                  <span class="msr">videocam</span>
                  <span>{{ heroFormatBadge }}</span>
                </div>
              </div>
              <div v-if="movie.poster" class="hpc-img">
                <img :src="movie.poster" :alt="movie.title" />
              </div>
              <div class="hpc-bottom">
                <div class="hpc-title">{{ movie.title.split(':')[0] || movie.title }}</div>
                <div class="hpc-sub" v-if="movie.englishTitle">
                  <span>{{ movie.englishTitle.toUpperCase() }}</span>
                </div>
                <div class="hpc-line"></div>
                <div class="hpc-meta">
                  <span class="hpc-meta-item">
                    <span class="msr">schedule</span>
                    <span>{{ movie.duration }} 分钟</span>
                  </span>
                  <span class="hpc-meta-item">
                    <span class="msr">calendar_today</span>
                    <span>{{ formatReleaseDate(movie.releaseDate) }}</span>
                  </span>
                </div>
              </div>
            </div>

            <!-- Hero Info -->
            <div class="hero-info">
              <div class="hero-info-top">
                <div class="hero-tags">
                  <span class="badge-now">
                    <span class="dot-now"></span>
                    <span>NOW SHOWING</span>
                  </span>
                </div>
                <h1 class="hero-title">{{ movie.title }}</h1>
                <p class="hero-en" v-if="movie.englishTitle">{{ movie.englishTitle.toUpperCase() }}</p>

                <div class="hero-genre-row">
                  <span v-for="g in genreList" :key="g" class="hero-genre-pill">{{ g }}</span>
                </div>

                <div class="hero-meta-row">
                  <span class="meta-item">
                    <span class="msr">schedule</span>
                    <span>{{ movie.duration }} 分钟</span>
                  </span>
                  <span class="meta-sep-dot">·</span>
                  <span class="meta-item">
                    <span class="msr">event</span>
                    <span>{{ formatReleaseDate(movie.releaseDate) }} 上映</span>
                  </span>
                  <span class="meta-sep-dot">·</span>
                  <span class="meta-item">
                    <span class="msr">translate</span>
                    <span>{{ movie.language || '英语·中字' }}</span>
                  </span>
                </div>

                <div class="hero-rating-block">
                  <div class="rating-main">
                    <span class="rating-big">{{ ratingDisplay }}</span>
                    <div class="rating-side">
                      <span class="rating-label">综合评分</span>
                      <div class="rating-stars">
                        <span v-for="i in 5" :key="i" class="msr star-ico">
                          {{ i <= Math.round(ratingNum / 2) ? 'star' : 'star_outline' }}
                        </span>
                      </div>
                    </div>
                  </div>
                  <div class="rating-sources">
                    <div class="rating-src">
                      <span class="rs-num">{{ (ratingNum + 0.3).toFixed(1) }}</span>
                      <span class="rs-label">猫眼</span>
                    </div>
                    <div class="rating-src">
                      <span class="rs-num">{{ (ratingNum - 0.4).toFixed(1) }}</span>
                      <span class="rs-label">豆瓣</span>
                    </div>
                    <div class="rating-src">
                      <span class="rs-num">{{ (ratingNum - 0.3).toFixed(1) }}</span>
                      <span class="rs-label">IMDb</span>
                    </div>
                    <div class="rating-src">
                      <span class="rs-num">486K</span>
                      <span class="rs-label">想看</span>
                    </div>
                  </div>
                </div>

                <p class="hero-desc">
                  {{ movie.description || '暂无简介' }}
                </p>
              </div>

              <div class="hero-actions">
                <button class="hero-cta" @click="scrollToBuy">
                  <span class="msr">play_arrow</span>
                  <span>立即购票</span>
                  <span class="hero-cta-price">起 ¥{{ minPrice }}</span>
                </button>
                <button class="hero-ghost" @click="tip('已加入想看')">
                  <span class="msr">favorite_border</span>
                  <span>想看</span>
                  <span class="hero-ghost-num">12.4K</span>
                </button>
                <button class="hero-icon" @click="tip('分享')">
                  <span class="msr">share</span>
                </button>
                <button
                  class="hero-icon"
                  aria-label="查看观众评价"
                  title="查看观众评价"
                  @click="scrollToReviews"
                >
                  <span class="msr">chat_bubble_outline</span>
                </button>
                <button class="hero-icon" @click="tip('想看')">
                  <span class="msr">bookmark_border</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- ============ MAIN COLUMNS ============ -->
        <section class="main-columns">
          <!-- LEFT COLUMN -->
          <div class="left-col">
            <!-- Synopsis Card -->
            <article class="card synopsis-card">
              <div class="card-head">
                <div class="card-head-left">
                  <span class="card-eyebrow">SYNOPSIS</span>
                  <h2 class="card-title">剧情简介</h2>
                </div>
                <button class="card-action" @click="tip('展开全文')">
                  <span>展开全文</span>
                  <span class="msr">expand_more</span>
                </button>
              </div>
              <p class="synopsis-text">
                {{ movie.description || '暂无简介' }}
              </p>
              <div class="synopsis-footer">
                <div class="sf-item">
                  <span class="sf-label">导演</span>
                  <span class="sf-val">{{ movie.director || '未知' }}</span>
                </div>
                <div class="sf-item">
                  <span class="sf-label">编剧</span>
                  <span class="sf-val">原著改编</span>
                </div>
                <div class="sf-item">
                  <span class="sf-label">出品方</span>
                  <span class="sf-val">CINEMA+ Studios</span>
                </div>
              </div>
            </article>

            <!-- Cast Card -->
            <article class="card cast-card">
              <div class="card-head">
                <div class="card-head-left">
                  <span class="card-eyebrow">CAST &amp; CREW</span>
                  <h2 class="card-title">演职员表</h2>
                </div>
                <button class="card-action" @click="tip('查看全部')">
                  <span>查看全部</span>
                  <span class="msr">arrow_forward</span>
                </button>
              </div>
              <div class="cast-grid">
                <div
                  v-for="(c, idx) in castList"
                  :key="idx"
                  class="cast-item"
                >
                  <div class="cast-avatar" :style="{ background: castGradient(idx) }">
                    <span class="cast-initial">{{ c.name.charAt(0) }}</span>
                  </div>
                  <span class="cast-name">{{ c.name }}</span>
                  <span class="cast-role">{{ c.role }}</span>
                </div>
              </div>
            </article>

            <!-- Stills Card -->
            <article class="card stills-card">
              <div class="card-head">
                <div class="card-head-left">
                  <span class="card-eyebrow">STILLS</span>
                  <h2 class="card-title">剧照</h2>
                </div>
                <button class="card-action" @click="tip('查看全部剧照')">
                  <span>全部 12 张</span>
                  <span class="msr">arrow_forward</span>
                </button>
              </div>
              <div class="stills-grid">
                <div
                  v-for="i in 6"
                  :key="i"
                  class="still-item"
                  :style="{ background: stillGradient(i) }"
                  @click="tip('查看剧照')"
                >
                  <span class="msr still-ico">image</span>
                </div>
              </div>
            </article>

            <!-- Trailer Card -->
            <article class="card trailer-card">
              <div class="card-head">
                <div class="card-head-left">
                  <span class="card-eyebrow">TRAILER</span>
                  <h2 class="card-title">官方预告片</h2>
                </div>
                <button class="card-action" @click="tip('全部预告')">
                  <span>3 个预告</span>
                  <span class="msr">arrow_forward</span>
                </button>
              </div>
              <div class="trailer-frame" @click="tip('播放预告片')">
                <div class="trailer-play">
                  <span class="msr">play_arrow</span>
                </div>
                <div class="trailer-meta">
                  <span class="trailer-title">官方正式预告 · 2 分 38 秒</span>
                  <span class="trailer-sub">中文字幕 · 4K HDR</span>
                </div>
              </div>
            </article>

            <!-- AI Similar Movies Card -->
            <article class="card ai-similar-card">
              <div class="card-head">
                <div class="card-head-left">
                  <div class="ai-eyebrow-row">
                    <span class="ai-icon-wrap">
                      <span class="msr">auto_awesome</span>
                    </span>
                    <span class="card-eyebrow purple">AI 相似推荐</span>
                  </div>
                  <h2 class="card-title">看了这部还想看</h2>
                </div>
                <button class="card-action" @click="loadSimilarMovies(movie.id)">
                  <span class="msr">refresh</span>
                  <span>换一批</span>
                </button>
              </div>

              <div v-if="similarDisplay.length > 0" class="similar-grid">
                <article
                  v-for="(m, idx) in similarDisplay"
                  :key="m.id"
                  class="similar-card"
                  @click="goToMovie(m.id)"
                >
                  <div class="similar-poster" :style="{ background: cardGradient(idx) }">
                    <img v-if="m.poster" :src="m.poster" :alt="m.title" />
                    <span class="similar-match">{{ matchScore(m.id) }}% 匹配</span>
                  </div>
                  <div class="similar-info">
                    <span class="similar-name">{{ m.title }}</span>
                    <span class="similar-meta">
                      <span class="msr">star</span>
                      <span>{{ m.rating?.toFixed(1) || '8.5' }}</span>
                      <span class="sep">·</span>
                      <span>{{ m.genre || '剧情' }}</span>
                    </span>
                  </div>
                </article>
              </div>
              <div v-else class="similar-empty">
                <span class="msr">auto_awesome</span>
                <p>暂无相似推荐</p>
              </div>

              <div class="ai-insight-banner">
                <span class="msr">auto_awesome</span>
                <span class="aib-text">
                  AI 分析:基于你对《{{ movie.title }}》的兴趣,以上 {{ similarDisplay.length }} 部影片的题材、风格和导演手法最为契合。
                </span>
              </div>
            </article>

            <!-- Reviews Card -->
            <article
              ref="reviewsCardRef"
              class="card reviews-card"
              :class="{ 'reviews-card--focus': reviewsFocused }"
            >
              <div class="card-head">
                <div class="card-head-left">
                  <span class="card-eyebrow">REVIEWS</span>
                  <h2 class="card-title">观众评价</h2>
                </div>
                <div class="reviews-summary" v-if="comments.length > 0">
                  <span class="rv-avg">{{ commentAvgStars.toFixed(1) }}</span>
                  <div class="rv-summary-side">
                    <div class="rv-stars-row">
                      <span v-for="i in 5" :key="i" class="msr rv-star">
                        {{ i <= Math.round(commentAvgStars) ? 'star' : 'star_outline' }}
                      </span>
                    </div>
                    <span class="rv-count">{{ comments.length }} 条评价</span>
                  </div>
                </div>
              </div>

              <!-- Write review entry -->
              <div class="review-action-bar">
                <button
                  v-if="commentEligibility.canComment"
                  class="review-write-btn"
                  @click="openReviewDialog"
                >
                  <span class="msr">rate_review</span>
                  <span>写评价</span>
                </button>
                <div v-else-if="commentEligibility.commented" class="review-owned-actions">
                  <div class="review-hint done">
                    <span class="msr">check_circle</span>
                    <span>我的评价：{{ commentEligibility.ownComment?.rating || 0 }} 星</span>
                  </div>
                  <button class="review-edit-btn" @click="openReviewDialog">
                    <span class="msr">edit</span>
                    <span>修改评价</span>
                  </button>
                </div>
                <div v-else class="review-hint">
                  <span class="msr">lock</span>
                  <span>购票观影后才能评价</span>
                </div>
              </div>

              <!-- Reviews list -->
              <div v-if="commentsLoading" class="reviews-loading">
                <span class="msr">progress_activity</span>
              </div>
              <div v-else-if="comments.length > 0" class="reviews-list">
                <div v-for="c in comments" :key="c.id" class="review-item">
                  <div class="review-avatar" :style="{ background: reviewAvatarGradient(c.userId) }">
                    <img v-if="c.userAvatar" :src="c.userAvatar" :alt="c.userName" />
                    <span v-else>{{ (c.userName || '影').charAt(0) }}</span>
                  </div>
                  <div class="review-body">
                    <div class="review-head">
                      <span class="review-name">{{ c.userName || '匿名影迷' }}</span>
                      <div class="review-stars">
                        <span v-for="i in 5" :key="i" class="msr rv-star sm">
                          {{ i <= c.rating ? 'star' : 'star_outline' }}
                        </span>
                      </div>
                      <span class="review-date">{{ formatReviewDate(c.createTime) }}</span>
                    </div>
                    <p v-if="c.content" class="review-text">{{ c.content }}</p>
                  </div>
                </div>
              </div>
              <div v-else class="reviews-empty">
                <span class="msr">reviews</span>
                <p>还没有评价，看完电影来抢沙发吧</p>
              </div>
            </article>
          </div>

          <!-- RIGHT COLUMN -->
          <div class="right-col">
            <!-- Buy Card -->
            <article class="card buy-card" ref="buyCardRef">
              <div class="buy-head">
                <div class="buy-head-left">
                  <span class="card-eyebrow primary">立即购票</span>
                  <h3 class="buy-title">今日 {{ showtimes.length }} 个场次</h3>
                </div>
                <span class="buy-trending">
                  <span class="msr">trending_up</span>
                  <span>TRENDING</span>
                </span>
              </div>

              <!-- Date Pills -->
              <div class="date-pills">
                <button
                  v-for="(d, idx) in dateList"
                  :key="d.value"
                  class="date-pill"
                  :class="{ active: idx === activeDateIdx }"
                  @click="onPickDate(idx)"
                >
                  <span class="dp-week">{{ d.week }}</span>
                  <span class="dp-day">{{ d.day }}</span>
                </button>
              </div>

              <!-- Cinema Selector -->
              <div class="cinema-select-wrap">
                <span class="msr cs-ico">location_on</span>
                <el-select
                  v-model="selectedCinemaId"
                  placeholder="请选择影院"
                  class="cinema-select"
                  popper-class="movie-detail-select-dropdown"
                >
                  <el-option
                    v-for="cinema in cinemas"
                    :key="cinema.id"
                    :label="cinema.name"
                    :value="cinema.id"
                  />
                </el-select>
              </div>

              <!-- Showtimes List -->
              <div v-loading="showtimesLoading" class="showtime-list-wrap">
                <div v-if="!selectedCinemaId" class="showtime-empty">
                  <span class="msr">theaters</span>
                  <p>请先选择影院查看场次</p>
                </div>
                <div v-else-if="showtimes.length === 0 && !showtimesLoading" class="showtime-empty">
                  <span class="msr">event_busy</span>
                  <p>该日期暂无可订场次</p>
                </div>
                <div v-else class="showtime-list">
                  <div
                    v-for="(st, idx) in showtimes"
                    :key="st.id"
                    class="showtime-row"
                    :class="{ featured: idx === 0 }"
                    @click="selectShowtime(st)"
                  >
                    <div class="st-time">
                      <span class="st-time-big">{{ formatTime(st.startTime) }}</span>
                      <span class="st-time-end">散场 {{ formatEndTime(st.startTime, movie.duration) }}</span>
                    </div>
                    <div class="st-info">
                      <span class="st-hall">{{ st.hallName }}</span>
                      <span class="st-format">IMAX · 国语 2D</span>
                    </div>
                    <div class="st-right">
                      <span class="st-price">¥{{ st.basePrice }}</span>
                      <span class="st-seats" :class="{ low: (st.availableSeats ?? 0) < 20 }">
                        剩 {{ st.availableSeats ?? 0 }} 座
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="buy-footer">
                <span class="msr">verified</span>
                <span>支持改签 · 选座保障 · 无忧购票</span>
              </div>
            </article>

            <!-- AI Friends Card -->
            <article class="card ai-friends-card">
              <div class="aifc-head">
                <div class="ai-icon-wrap purple">
                  <span class="msr">groups</span>
                </div>
                <div class="aifc-title-block">
                  <span class="aifc-title">AI 同好观影</span>
                  <span class="aifc-sub">智能匹配 · 团购 8.5 折</span>
                </div>
                <span class="aifc-beta">BETA</span>
              </div>
              <p class="aifc-desc">
                AI 根据偏好为你匹配同好,组团观影享 8.5 折优惠,还能解锁专属包场福利。
              </p>
              <div class="friends-list">
                <div v-for="f in friends" :key="f.name" class="friend-row">
                  <div class="friend-avatar" :style="{ background: f.color }">{{ f.name.charAt(0) }}</div>
                  <div class="friend-info">
                    <span class="friend-name">{{ f.name }}</span>
                    <span class="friend-meta">{{ f.tag }}</span>
                  </div>
                  <span class="friend-match">{{ f.match }}%</span>
                </div>
              </div>
              <div class="aifc-actions">
                <button class="aifc-btn primary" @click="tip('发起组团')">
                  <span class="msr">campaign</span>
                  <span>发起组团</span>
                </button>
                <button class="aifc-btn ghost" @click="tip('浏览同好')">
                  <span class="msr">visibility</span>
                  <span>浏览</span>
                </button>
              </div>
            </article>

            <!-- AI Helper Card -->
            <article class="card ai-helper-card">
              <div class="ahc-head">
                <div class="ai-icon-wrap">
                  <span class="msr">smart_toy</span>
                </div>
                <div class="ahc-title-block">
                  <span class="ahc-title">AI 观影助手</span>
                  <span class="ahc-sub">关于本片,你想了解什么?</span>
                </div>
              </div>
              <div class="ahc-questions">
                <button
                  v-for="q in hotQuestions"
                  :key="q"
                  class="ahc-q"
                  @click="askAi(q)"
                >
                  {{ q }}
                </button>
              </div>
              <div class="ahc-input">
                <input
                  type="text"
                  v-model="aiQuestion"
                  placeholder="问点什么..."
                  @keyup.enter="askAi(aiQuestion)"
                />
                <button class="ahc-send" @click="askAi(aiQuestion)">
                  <span class="msr">send</span>
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- Page Footer -->
        <div class="page-foot">
          <span>© 2026 CINEMA+ · 沪ICP 备 2024XXXXX 号</span>
          <div class="foot-links">
            <span @click="tip('帮助中心')">帮助中心</span>
            <span @click="tip('隐私政策')">隐私政策</span>
            <span @click="tip('用户协议')">用户协议</span>
          </div>
        </div>
      </div>
    </main>

    <!-- ============ 写评价对话框 ============ -->
    <el-dialog
      v-model="showReviewDialog"
      :title="commentEligibility.ownComment ? '修改评价' : '评价电影'"
      width="480px"
      :close-on-click-modal="false"
      class="review-dialog"
    >
      <div class="review-form" v-if="movie">
        <div class="rf-movie">
          <div class="rf-poster" :style="{ background: heroPosterGradient }">
            <img v-if="movie.poster" :src="movie.poster" :alt="movie.title" />
          </div>
          <div class="rf-movie-info">
            <span class="rf-movie-title">{{ movie.title }}</span>
            <span class="rf-movie-sub">{{ movie.genre || '剧情' }} · {{ movie.duration }} 分钟</span>
          </div>
        </div>

        <div class="rf-rate-row">
          <span class="rf-label">我的评分</span>
          <el-rate v-model="reviewForm.rating" :max="5" size="large" />
          <span class="rf-rate-text">{{ rateText(reviewForm.rating) }}</span>
        </div>

        <el-input
          v-model="reviewForm.content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说说你的观影感受（选填）..."
        />
      </div>

      <template #footer>
        <div class="review-dialog-footer">
          <button class="rd-btn cancel" @click="showReviewDialog = false">取消</button>
          <button class="rd-btn primary" @click="submitReview" :disabled="submittingReview">
            <span class="msr" v-if="submittingReview">progress_activity</span>
            <span>{{ submittingReview ? '提交中...' : (commentEligibility.ownComment ? '保存修改' : '发布评价') }}</span>
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { movieApi } from '@/api/movie'
import { cinemaApi } from '@/api/cinema'
import { showtimeApi } from '@/api/showtime'
import { commentApi } from '@/api/comment'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import type { Movie, Cinema, Showtime, Comment, CommentEligibility } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const movie = ref<Movie | null>(null)
const cinemas = ref<Cinema[]>([])
const selectedCinemaId = ref<number>()
const selectedDate = ref(new Date())
const showtimes = ref<Showtime[]>([])
const showtimesLoading = ref(false)
const similarMovies = ref<Movie[]>([])
const activeDateIdx = ref(0)
const activeNav = ref('movies')
const searchKeyword = ref('')
const aiQuestion = ref('')
const buyCardRef = ref<HTMLElement | null>(null)
const reviewsCardRef = ref<HTMLElement | null>(null)
const reviewsFocused = ref(false)
let reviewsFocusTimer: ReturnType<typeof setTimeout> | undefined

const comments = ref<Comment[]>([])
const commentsLoading = ref(false)
const commentEligibility = ref<CommentEligibility>({ watched: false, commented: false, canComment: false })
const showReviewDialog = ref(false)
const submittingReview = ref(false)
const reviewForm = ref({ rating: 5, content: '' })

const navItems = [
  { key: 'home', label: '首页', icon: 'home', route: '/home' },
  { key: 'movies', label: '影片', icon: 'movie', badge: 'NEW', badgeClass: 'badge-primary', route: '/movies' },
  { key: 'cinemas', label: '影院', icon: 'theaters', route: '/cinemas' },
  { key: 'profile', label: '个人中心', icon: 'person', route: '/profile' },
  { key: 'events', label: '演出', icon: 'event' },
  { key: 'orders', label: '订单', icon: 'receipt_long', route: '/orders' },
  { key: 'favorites', label: '想看', icon: 'favorite' },
  { key: 'settings', label: '设置', icon: 'settings' }
]

const friends = [
  { name: '陈思源', tag: '硬核科幻 · 92% 重合', match: 96, color: 'linear-gradient(135deg, #A855F7, #EC4899)' },
  { name: '林晓彤', tag: '诺兰粉丝 · 88% 重合', match: 91, color: 'linear-gradient(135deg, #0EA5E9, #6366F1)' },
  { name: '王梓豪', tag: 'IMAX 体验 · 85% 重合', match: 87, color: 'linear-gradient(135deg, #F59E0B, #EF4444)' }
]

const hotQuestions = [
  '这部电影适合带小孩看吗?',
  '需要看前作才能理解吗?',
  '有片尾彩蛋吗?',
  'IMAX 厅值得加价吗?'
]

const castList = computed(() => {
  if (!movie.value) return []
  const actors = (movie.value.actors || '').split(/[,，、\s]+/).filter(Boolean)
  const base: { name: string; role: string }[] = [
    { name: movie.value.director || '导演', role: '导演' }
  ]
  actors.slice(0, 6).forEach(a => base.push({ name: a, role: '主演' }))
  while (base.length < 7) {
    base.push({ name: '主演' + base.length, role: '主演' })
  }
  return base.slice(0, 7)
})

const genreList = computed(() => {
  if (!movie.value?.genre) return ['剧情']
  return movie.value.genre.split(/[\/\s,、]+/).filter(Boolean).slice(0, 5)
})

const ratingNum = computed(() => movie.value?.rating ?? 8.8)
const ratingDisplay = computed(() => ratingNum.value.toFixed(1))

const minPrice = computed(() => {
  if (showtimes.value.length === 0) return 58
  return Math.min(...showtimes.value.map(s => s.basePrice))
})

const similarDisplay = computed(() => similarMovies.value.slice(0, 4))

const commentAvgStars = computed(() => {
  if (comments.value.length === 0) return 0
  const sum = comments.value.reduce((acc, c) => acc + c.rating, 0)
  return sum / comments.value.length
})

const heroPosterGradient = computed(() => {
  const palette = [
    'linear-gradient(180deg, #D08850 0%, #7A3A20 50%, #1f1815 100%)',
    'linear-gradient(180deg, #4A5FB8 0%, #1A2660 50%, #0A1230 100%)',
    'linear-gradient(180deg, #8B2F5C 0%, #4A1530 50%, #1F0820 100%)',
    'linear-gradient(180deg, #3B7A4F 0%, #1F4530 50%, #0A1F15 100%)'
  ]
  const id = movie.value?.id || 1
  return palette[id % palette.length]
})

const heroFormatBadge = computed(() => {
  const formats = ['IMAX · 3D', 'IMAX', 'CINITY', '杜比全景声']
  const id = movie.value?.id || 1
  return formats[id % formats.length]
})

const dateList = computed(() => {
  const arr: { value: string; week: string; day: string }[] = []
  const weekNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 5; i++) {
    const d = new Date()
    d.setDate(d.getDate() + i)
    arr.push({
      value: d.toISOString().split('T')[0],
      week: i === 0 ? '今日' : i === 1 ? '明日' : weekNames[d.getDay()],
      day: `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}`
    })
  }
  return arr
})

const todayLabel = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd} ${week}`
})

const avatarChar = computed(() => {
  const n = userStore.userInfo?.nickname || '用'
  return n.charAt(0).toUpperCase()
})

const cardGradients = [
  'linear-gradient(200deg, #1a3a5c 0%, #0a1f3a 100%)',
  'linear-gradient(200deg, #5c2a1a 0%, #2a0f08 100%)',
  'linear-gradient(200deg, #5c1a4a 0%, #2a0820 100%)',
  'linear-gradient(200deg, #5c5a1a 0%, #2a280a 100%)',
  'linear-gradient(200deg, #FF6B9D 0%, #8B2F5C 100%)',
  'linear-gradient(200deg, #1a5c4a 0%, #082a20 100%)'
]
const cardGradient = (i: number) => cardGradients[i % cardGradients.length]

const castGradients = [
  'linear-gradient(135deg, #A855F7, #EC4899)',
  'linear-gradient(135deg, #0EA5E9, #6366F1)',
  'linear-gradient(135deg, #F59E0B, #EF4444)',
  'linear-gradient(135deg, #22C55E, #0EA5E9)',
  'linear-gradient(135deg, #EC4899, #F59E0B)',
  'linear-gradient(135deg, #6366F1, #A855F7)',
  'linear-gradient(135deg, #EF4444, #EC4899)'
]
const castGradient = (i: number) => castGradients[i % castGradients.length]

const stillGradients = [
  'linear-gradient(135deg, #2a1a3a 0%, #0a0815 100%)',
  'linear-gradient(135deg, #3a2a1a 0%, #150a08 100%)',
  'linear-gradient(135deg, #1a3a2a 0%, #08150a 100%)',
  'linear-gradient(135deg, #3a1a2a 0%, #150815 100%)',
  'linear-gradient(135deg, #1a2a3a 0%, #08150f 100%)',
  'linear-gradient(135deg, #2a3a1a 0%, #15150a 100%)'
]
const stillGradient = (i: number) => stillGradients[(i - 1) % stillGradients.length]

const matchScore = (id: number) => 80 + (id % 20)

const formatTime = (datetime: string) => {
  const date = new Date(datetime)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

const formatEndTime = (start: string, duration: number) => {
  const d = new Date(start)
  d.setMinutes(d.getMinutes() + (duration || 120))
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

const formatReleaseDate = (date?: string) => {
  if (!date) return '——'
  const d = new Date(date)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

const loadMovie = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    movie.value = await movieApi.getById(id)
    loadSimilarMovies(id)
    loadComments(id)
    loadEligibility(id)
  } catch (error) {
    console.error('加载电影失败:', error)
  } finally {
    loading.value = false
  }
}

const loadSimilarMovies = async (movieId: number) => {
  try {
    const res = await request.get<any, Movie[]>(`/recommend/similar/${movieId}`, {
      params: { limit: 6 }
    })
    similarMovies.value = res || []
  } catch (error) {
    console.error('加载相似电影失败:', error)
  }
}

const loadComments = async (movieId: number) => {
  commentsLoading.value = true
  try {
    comments.value = await commentApi.listByMovie(movieId)
  } catch (error) {
    console.error('加载评价失败:', error)
  } finally {
    commentsLoading.value = false
  }
}

const loadEligibility = async (movieId: number) => {
  if (!userStore.isLoggedIn) {
    commentEligibility.value = { watched: false, commented: false, canComment: false }
    return
  }
  try {
    commentEligibility.value = await commentApi.checkEligibility(movieId)
  } catch (error) {
    console.error('加载评价资格失败:', error)
  }
}

const openReviewDialog = () => {
  const ownComment = commentEligibility.value.ownComment
  reviewForm.value = ownComment
    ? { rating: ownComment.rating, content: ownComment.content || '' }
    : { rating: 5, content: '' }
  showReviewDialog.value = true
}

const submitReview = async () => {
  if (!movie.value) return
  if (!reviewForm.value.rating) {
    ElMessage.warning('请先打分')
    return
  }
  submittingReview.value = true
  try {
    const ownComment = commentEligibility.value.ownComment
    if (ownComment) {
      await commentApi.update(ownComment.id, {
        rating: reviewForm.value.rating,
        content: reviewForm.value.content.trim() || undefined
      })
      ElMessage.success('评价已更新')
    } else {
      await commentApi.create({
        movieId: movie.value.id,
        rating: reviewForm.value.rating,
        content: reviewForm.value.content.trim() || undefined
      })
      ElMessage.success('评价成功')
    }
    showReviewDialog.value = false
    await Promise.all([loadComments(movie.value.id), loadEligibility(movie.value.id)])
    movie.value = await movieApi.getById(movie.value.id)
  } catch (error) {
    console.error('评价失败:', error)
  } finally {
    submittingReview.value = false
  }
}

const rateText = (rating: number) => {
  const map: Record<number, string> = { 1: '很差', 2: '较差', 3: '还行', 4: '推荐', 5: '力荐' }
  return map[rating] || ''
}

const formatReviewDate = (date?: string) => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

const reviewAvatarGradients = [
  'linear-gradient(135deg, #A855F7, #EC4899)',
  'linear-gradient(135deg, #0EA5E9, #6366F1)',
  'linear-gradient(135deg, #F59E0B, #EF4444)',
  'linear-gradient(135deg, #22C55E, #0EA5E9)',
  'linear-gradient(135deg, #EC4899, #F59E0B)'
]
const reviewAvatarGradient = (id: number) => reviewAvatarGradients[id % reviewAvatarGradients.length]

const loadCinemas = async () => {
  try {
    cinemas.value = await cinemaApi.getAll()
    if (cinemas.value.length > 0 && !selectedCinemaId.value) {
      selectedCinemaId.value = cinemas.value[0].id
    }
  } catch (error) {
    console.error('加载影院失败:', error)
  }
}

const loadShowtimes = async () => {
  if (!selectedCinemaId.value || !movie.value) return

  showtimesLoading.value = true
  try {
    const dateStr = selectedDate.value.toISOString().split('T')[0]
    showtimes.value = await showtimeApi.getList({
      movieId: movie.value.id,
      cinemaId: selectedCinemaId.value,
      date: dateStr
    })
  } catch (error) {
    console.error('加载场次失败:', error)
  } finally {
    showtimesLoading.value = false
  }
}

const onPickDate = (idx: number) => {
  activeDateIdx.value = idx
  const d = new Date()
  d.setDate(d.getDate() + idx)
  selectedDate.value = d
}

const selectShowtime = (showtime: Showtime) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (showtime.availableSeats === 0) {
    ElMessage.warning('该场次已售罄')
    return
  }
  router.push(`/showtime/${showtime.id}/seats`)
}

const goToMovie = (id: number) => {
  router.push(`/movie/${id}`)
}

const scrollToBuy = () => {
  nextTick(() => {
    const el = buyCardRef.value as any
    const node = el?.$el || el
    node?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  })
}

const scrollToReviews = () => {
  nextTick(() => {
    reviewsCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    reviewsFocused.value = false
    requestAnimationFrame(() => {
      reviewsFocused.value = true
      if (reviewsFocusTimer) clearTimeout(reviewsFocusTimer)
      reviewsFocusTimer = setTimeout(() => {
        reviewsFocused.value = false
      }, 1400)
    })
  })
}

const askAi = (q: string) => {
  if (!q.trim()) return
  ElMessage.info(`AI 助手即将回答:"${q}"`)
  aiQuestion.value = ''
}

const onNavClick = (item: { key: string; route?: string }) => {
  activeNav.value = item.key
  if (item.route) {
    if ((item.route === '/orders' || item.route === '/profile') && !userStore.isLoggedIn) {
      ElMessage.warning('请先登录')
      router.push('/login')
      return
    }
    router.push(item.route)
  } else {
    ElMessage.info(`${navItems.find(n => n.key === item.key)?.label} 模块敬请期待`)
  }
}

const tip = (label: string) => {
  ElMessage.info(`${label} 功能敬请期待`)
}

const onSearch = () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/movies', query: { q: kw } })
}

const handleLogout = () => {
  userStore.logout()
  ElMessage.success('已退出登录')
}

watch([selectedCinemaId, selectedDate], () => {
  loadShowtimes()
})

watch(() => route.params.id, (newId) => {
  if (newId) loadMovie()
})

onMounted(() => {
  loadMovie()
  loadCinemas()
})

onBeforeUnmount(() => {
  if (reviewsFocusTimer) clearTimeout(reviewsFocusTimer)
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Geist:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0&display=swap');

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
  cursor: pointer;
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
.brand-sub { font-size: 12px; color: var(--sidebar-fg); }

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
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--sidebar-fg);
}
.qa-head .msr { font-size: 16px; }
.qa-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
  text-decoration: none;
  color: inherit;
}
.qa-item:hover { background: rgba(0, 0, 0, 0.04); }
.qa-ico { font-size: 18px !important; color: var(--sidebar-fg); }
.qa-label { flex: 1; font-size: 14px; color: var(--sidebar-accent-fg); }
.qa-tag {
  padding: 3px 8px;
  border-radius: 4px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
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
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.4);
}
.guest-avatar { background: var(--sidebar-accent); color: var(--sidebar-fg); }
.user-info { flex: 1; display: flex; flex-direction: column; gap: 2px; cursor: pointer; transition: opacity 0.15s; }
.user-info:hover { opacity: 0.8; }
.user-name { font-size: 14px; font-weight: 600; color: var(--sidebar-accent-fg); }
.user-tier {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--sidebar-fg);
}
.more-btn { cursor: pointer; color: var(--sidebar-fg); }
.footer-login {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

/* ============ MAIN AREA ============ */
.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 32px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 50;
  backdrop-filter: blur(8px);
}
.tb-back {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--card);
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  cursor: pointer;
  transition: all 0.15s;
}
.tb-back:hover { border-color: var(--primary); color: var(--primary); }
.tb-back .msr { font-size: 16px; }
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
.tb-search-ico { cursor: pointer; transition: color 0.15s; }
.tb-search-ico:hover { color: var(--primary); }
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

/* Loading */
.page-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px;
}
.loading-spin .msr {
  font-size: 48px;
  color: var(--primary);
  animation: spin 1.2s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Content */
.content {
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 28px 32px;
}

/* ============ HERO ============ */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  background: linear-gradient(180deg, #3D1B0F 0%, #1A0A05 60%, #0A0604 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  min-height: 620px;
}
.hero-bg-num {
  position: absolute;
  right: -40px;
  top: -120px;
  font-family: var(--font-mono);
  font-size: 600px;
  font-weight: 900;
  line-height: 1;
  color: rgba(255, 255, 255, 0.03);
  pointer-events: none;
  user-select: none;
}
.hero-inner {
  position: relative;
  display: flex;
  gap: 64px;
  padding: 56px 64px;
  align-items: stretch;
}

/* Hero Poster Card */
.hero-poster-card {
  flex-shrink: 0;
  width: 360px;
  min-height: 520px;
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  position: relative;
  overflow: hidden;
}
.hpc-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hpc-num {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.85);
  letter-spacing: 2px;
}
.hpc-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}
.hpc-tag .msr { font-size: 14px; }
.hpc-img {
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.5;
}
.hpc-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hpc-top, .hpc-bottom { position: relative; z-index: 1; }
.hpc-bottom { display: flex; flex-direction: column; gap: 8px; }
.hpc-title {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: #fff;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
}
.hpc-sub {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.75);
}
.hpc-line {
  height: 1px;
  background: rgba(255, 255, 255, 0.2);
  margin: 8px 0;
}
.hpc-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hpc-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.hpc-meta-item .msr { font-size: 14px; }

/* Hero Info */
.hero-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 32px;
  min-width: 0;
}
.hero-info-top {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.hero-tags { display: flex; align-items: center; gap: 8px; }
.badge-now {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: rgba(255, 132, 0, 0.15);
  border: 1px solid rgba(255, 132, 0, 0.45);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--primary);
}
.dot-now {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--primary);
  box-shadow: 0 0 8px var(--primary);
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.hero-title {
  margin: 0;
  font-size: 64px;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: -1.5px;
  color: #fff;
}
.hero-en {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.5);
}

.hero-genre-row { display: flex; flex-wrap: wrap; gap: 8px; }
.hero-genre-pill {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.hero-meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}
.meta-item .msr { font-size: 15px; color: rgba(255, 255, 255, 0.5); }
.meta-sep-dot { color: rgba(255, 255, 255, 0.3); }

/* Hero rating block */
.hero-rating-block {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 18px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-wrap: wrap;
}
.rating-main { display: flex; align-items: center; gap: 16px; }
.rating-big {
  font-family: var(--font-mono);
  font-size: 72px;
  font-weight: 800;
  line-height: 1;
  color: var(--primary);
  letter-spacing: -2px;
}
.rating-side { display: flex; flex-direction: column; gap: 6px; }
.rating-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
}
.rating-stars { display: flex; gap: 2px; }
.star-ico { font-size: 18px !important; color: var(--primary); }

.rating-sources {
  display: flex;
  gap: 24px;
  padding-left: 24px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
}
.rating-src { display: flex; flex-direction: column; gap: 2px; }
.rs-num {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.rs-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.hero-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.75);
  max-width: 720px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Hero actions */
.hero-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hero-cta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  border: none;
  border-radius: 999px;
  background: var(--primary);
  color: var(--primary-foreground);
  font-family: var(--font-sans);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(255, 132, 0, 0.4);
  transition: transform 0.15s, box-shadow 0.2s;
}
.hero-cta:hover { transform: translateY(-1px); box-shadow: 0 10px 28px rgba(255, 132, 0, 0.55); }
.hero-cta .msr { font-size: 20px; }
.hero-cta-price {
  padding: 3px 9px;
  border-radius: 6px;
  background: rgba(17, 17, 17, 0.18);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
}

.hero-ghost {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 22px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}
.hero-ghost:hover { background: rgba(255, 255, 255, 0.14); }
.hero-ghost .msr { font-size: 18px; }
.hero-ghost-num {
  font-family: var(--font-mono);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.hero-icon {
  width: 48px;
  height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}
.hero-icon:hover { background: rgba(255, 255, 255, 0.14); }

/* ============ MAIN COLUMNS ============ */
.main-columns {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  align-items: start;
}

.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
}
.card-head-left { display: flex; flex-direction: column; gap: 6px; }
.card-eyebrow {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--muted);
}
.card-eyebrow.primary { color: var(--primary); }
.card-eyebrow.purple { color: #A855F7; }
.card-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--fg);
}
.card-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s;
}
.card-action:hover { border-color: var(--primary); color: var(--primary); }
.card-action .msr { font-size: 14px; }

/* Left column */
.left-col { display: flex; flex-direction: column; gap: 24px; min-width: 0; }

/* Synopsis */
.synopsis-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--fg);
}
.synopsis-footer {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.sf-item { display: flex; flex-direction: column; gap: 4px; }
.sf-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--muted);
}
.sf-val { font-size: 14px; font-weight: 600; color: var(--fg); }

/* Cast */
.cast-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
}
.cast-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.cast-avatar {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--card);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.15s;
}
.cast-item:hover .cast-avatar { transform: scale(1.05); }
.cast-initial {
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.9);
}
.cast-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.cast-role {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
}

/* Stills */
.stills-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.still-item {
  height: 130px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.15s;
  border: 1px solid var(--border);
}
.still-item:hover { transform: translateY(-2px); }
.still-ico {
  font-size: 36px !important;
  color: rgba(255, 255, 255, 0.3);
}

/* Trailer */
.trailer-frame {
  position: relative;
  height: 380px;
  border-radius: 16px;
  background: linear-gradient(135deg, #1a0f08 0%, #3d1f10 100%);
  border: 1px solid var(--border);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.trailer-play {
  width: 80px;
  height: 80px;
  border-radius: 999px;
  background: rgba(255, 132, 0, 0.95);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(255, 132, 0, 0.5);
  transition: transform 0.2s;
}
.trailer-frame:hover .trailer-play { transform: scale(1.1); }
.trailer-play .msr {
  font-size: 44px !important;
  color: #fff;
}
.trailer-meta {
  position: absolute;
  left: 24px;
  bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #fff;
}
.trailer-title { font-size: 15px; font-weight: 600; }
.trailer-sub {
  font-family: var(--font-mono);
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
}

/* AI Similar */
.ai-similar-card {
  border-color: rgba(168, 85, 247, 0.25);
  box-shadow: 0 8px 24px rgba(168, 85, 247, 0.12);
}
.ai-eyebrow-row { display: flex; align-items: center; gap: 10px; }
.ai-icon-wrap {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #A855F7 0%, #EC4899 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(168, 85, 247, 0.35);
}
.ai-icon-wrap.purple { background: linear-gradient(135deg, #A855F7 0%, #7C3AED 100%); }
.ai-icon-wrap .msr { font-size: 16px; }

.similar-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.similar-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  transition: transform 0.15s;
}
.similar-card:hover { transform: translateY(-3px); }
.similar-poster {
  position: relative;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.similar-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.similar-match {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 3px 8px;
  background: rgba(168, 85, 247, 0.85);
  backdrop-filter: blur(4px);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  color: #fff;
}
.similar-info { display: flex; flex-direction: column; gap: 3px; padding: 0 2px; }
.similar-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.similar-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--muted);
}
.similar-meta .msr { font-size: 12px; color: var(--primary); }
.similar-meta .sep { color: var(--border); }

.similar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 16px;
  border: 1.5px dashed var(--border);
  border-radius: 14px;
  color: var(--muted);
}
.similar-empty .msr { font-size: 36px; }
.similar-empty p { margin: 0; font-size: 13px; }

.ai-insight-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  background: linear-gradient(90deg, rgba(168, 85, 247, 0.1) 0%, rgba(236, 72, 153, 0.06) 100%);
  border: 1px solid rgba(168, 85, 247, 0.25);
}
.ai-insight-banner .msr {
  font-size: 18px;
  color: #A855F7;
  flex-shrink: 0;
}
.aib-text {
  font-size: 12px;
  line-height: 1.5;
  color: var(--fg);
}

/* Right column */
.right-col {
  display: flex;
  flex-direction: column;
  gap: 24px;
  position: sticky;
  top: 88px;
}

/* Reviews Card */
.reviews-card {
  scroll-margin-top: 92px;
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.25s;
}
.reviews-card--focus {
  border-color: rgba(255, 132, 0, 0.75);
  box-shadow: 0 0 0 4px rgba(255, 132, 0, 0.12), 0 18px 36px rgba(17, 17, 17, 0.08);
  transform: translateY(-2px);
}
.reviews-summary {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rv-avg {
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
  color: var(--primary);
}
.rv-summary-side { display: flex; flex-direction: column; gap: 4px; }
.rv-stars-row { display: flex; gap: 1px; }
.rv-star { font-size: 16px !important; color: var(--primary); }
.rv-star.sm { font-size: 14px !important; }
.rv-count { font-size: 11px; color: var(--muted); }

.review-action-bar {
  display: flex;
  padding-bottom: 4px;
}
.review-write-btn {
  display: inline-flex;
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
  transition: transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 4px 12px rgba(255, 132, 0, 0.25);
}
.review-write-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35); }
.review-write-btn .msr { font-size: 16px; }
.review-hint {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--secondary);
  border: 1px solid var(--border);
  font-size: 13px;
  color: var(--muted);
}
.review-hint .msr { font-size: 16px; }
.review-hint.done { color: #22C55E; border-color: rgba(34, 197, 94, 0.4); background: rgba(34, 197, 94, 0.08); }
.review-hint.done .msr { color: #22C55E; }
.review-owned-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.review-edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  border: 1px solid rgba(168, 85, 247, 0.35);
  border-radius: 10px;
  background: rgba(168, 85, 247, 0.08);
  color: #9333EA;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.review-edit-btn:hover { background: rgba(168, 85, 247, 0.14); transform: translateY(-1px); }
.review-edit-btn .msr { font-size: 15px; }

.reviews-loading {
  display: flex;
  justify-content: center;
  padding: 32px;
}
.reviews-loading .msr { font-size: 32px; color: var(--primary); animation: spin 1.2s linear infinite; }

.reviews-list { display: flex; flex-direction: column; gap: 16px; }
.review-item { display: flex; gap: 12px; }
.review-avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  overflow: hidden;
}
.review-avatar img { width: 100%; height: 100%; object-fit: cover; }
.review-body { flex: 1; display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.review-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.review-name { font-size: 14px; font-weight: 600; color: var(--fg); }
.review-stars { display: flex; gap: 1px; }
.review-date {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--muted);
}
.review-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--fg);
}
.reviews-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 16px;
  border: 1.5px dashed var(--border);
  border-radius: 14px;
  color: var(--muted);
}
.reviews-empty .msr { font-size: 36px; }
.reviews-empty p { margin: 0; font-size: 13px; }

/* Review Dialog */
.review-form { display: flex; flex-direction: column; gap: 20px; }
.rf-movie { display: flex; gap: 14px; align-items: center; }
.rf-poster {
  width: 56px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}
.rf-poster img { width: 100%; height: 100%; object-fit: cover; }
.rf-movie-info { display: flex; flex-direction: column; gap: 4px; }
.rf-movie-title { font-size: 16px; font-weight: 700; color: var(--fg); }
.rf-movie-sub { font-size: 12px; color: var(--muted); }
.rf-rate-row { display: flex; align-items: center; gap: 12px; }
.rf-label { font-size: 14px; font-weight: 600; color: var(--fg); }
.rf-rate-text { font-size: 13px; font-weight: 600; color: var(--primary); }

.review-dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
.rd-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  border: none;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.rd-btn.cancel { background: var(--secondary); color: var(--fg); border: 1px solid var(--border); }
.rd-btn.cancel:hover { background: var(--border); }
.rd-btn.primary { background: var(--primary); color: var(--primary-foreground); }
.rd-btn.primary:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(255, 132, 0, 0.35); }
.rd-btn.primary:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }
.rd-btn .msr { font-size: 16px; animation: spin 1s linear infinite; }

/* Buy Card */
.buy-card { padding: 22px; }
.buy-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.buy-head-left { display: flex; flex-direction: column; gap: 4px; }
.buy-title { margin: 0; font-size: 20px; font-weight: 700; color: var(--fg); }
.buy-trending {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(255, 132, 0, 0.13);
  border: 1px solid rgba(255, 132, 0, 0.4);
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1.2px;
  color: var(--primary);
}
.buy-trending .msr { font-size: 12px; }

.date-pills {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
}
.date-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 4px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--card);
  cursor: pointer;
  transition: all 0.15s;
}
.date-pill:hover { border-color: var(--primary); }
.date-pill.active {
  background: var(--primary);
  border-color: var(--primary);
}
.date-pill.active .dp-week,
.date-pill.active .dp-day {
  color: var(--primary-foreground);
}
.dp-week { font-size: 11px; font-weight: 600; color: var(--muted); }
.dp-day {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  color: var(--fg);
}

.cinema-select-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 14px 4px 14px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.cs-ico { font-size: 18px; color: var(--primary); }
.cinema-select {
  flex: 1;
}
.cinema-select :deep(.el-select__wrapper) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 8px 0 !important;
}
.cinema-select :deep(.el-input__inner) {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--fg);
}

.showtime-list-wrap { min-height: 80px; }
.showtime-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 12px;
  text-align: center;
}
.showtime-empty .msr {
  font-size: 36px !important;
  color: var(--muted);
}
.showtime-empty p {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.showtime-list { display: flex; flex-direction: column; gap: 8px; }
.showtime-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: var(--card);
}
.showtime-row:hover {
  border-color: var(--primary);
  transform: translateX(2px);
}
.showtime-row.featured {
  background: rgba(255, 132, 0, 0.08);
  border-color: rgba(255, 132, 0, 0.4);
}
.st-time { display: flex; flex-direction: column; gap: 2px; min-width: 70px; }
.st-time-big {
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 700;
  color: var(--fg);
}
.st-time-end {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
}
.st-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.st-hall {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st-format {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
}
.st-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.st-price {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 800;
  color: var(--primary);
}
.st-seats {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
}
.st-seats.low { color: #EF4444; }

.buy-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--muted);
}
.buy-footer .msr { font-size: 14px; color: #22C55E; }

/* AI Friends Card */
.ai-friends-card {
  background: linear-gradient(135deg, #1F1340 0%, #2D1B4E 50%, #1A0F2E 100%);
  border-color: rgba(168, 85, 247, 0.4);
  box-shadow: 0 12px 32px rgba(168, 85, 247, 0.25);
  color: #fff;
}
.aifc-head { display: flex; align-items: center; gap: 12px; }
.aifc-title-block { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.aifc-title {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: #fff;
}
.aifc-sub { font-size: 11px; color: rgba(255, 255, 255, 0.55); }
.aifc-beta {
  padding: 3px 8px;
  background: rgba(168, 85, 247, 0.25);
  border: 1px solid #A855F7;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
}
.aifc-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.78);
}
.friends-list { display: flex; flex-direction: column; gap: 8px; }
.friend-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
}
.friend-avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}
.friend-info { flex: 1; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.friend-name { font-size: 13px; font-weight: 600; color: #fff; }
.friend-meta {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.55);
}
.friend-match {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 800;
  color: #C084FC;
}
.aifc-actions { display: flex; gap: 8px; }
.aifc-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 11px 12px;
  border-radius: 10px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  border: none;
  transition: transform 0.15s;
}
.aifc-btn.primary {
  background: linear-gradient(135deg, #A855F7 0%, #EC4899 100%);
  color: #fff;
  box-shadow: 0 6px 16px rgba(168, 85, 247, 0.35);
}
.aifc-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #fff;
}
.aifc-btn:hover { transform: translateY(-1px); }
.aifc-btn .msr { font-size: 14px; }

/* AI Helper Card */
.ai-helper-card {
  border-color: rgba(168, 85, 247, 0.25);
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.04) 0%, rgba(255, 255, 255, 1) 100%);
}
.ahc-head { display: flex; align-items: center; gap: 12px; }
.ahc-title-block { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.ahc-title {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 700;
  color: var(--fg);
}
.ahc-sub { font-size: 12px; color: var(--muted); }
.ahc-questions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.ahc-q {
  padding: 10px 12px;
  background: var(--secondary);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  color: var(--fg);
  text-align: left;
  cursor: pointer;
  line-height: 1.4;
  transition: all 0.15s;
}
.ahc-q:hover {
  border-color: #A855F7;
  color: #A855F7;
  background: rgba(168, 85, 247, 0.05);
}
.ahc-input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 4px 4px 14px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.ahc-input:focus-within { border-color: #A855F7; }
.ahc-input input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--fg);
  padding: 8px 0;
}
.ahc-input input::placeholder { color: var(--muted); }
.ahc-send {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #A855F7 0%, #EC4899 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(168, 85, 247, 0.35);
  transition: transform 0.15s;
}
.ahc-send:hover { transform: translateY(-1px); }
.ahc-send .msr { font-size: 16px; }

/* Page foot */
.page-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 4px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--muted);
}
.foot-links { display: flex; gap: 16px; }
.foot-links span { cursor: pointer; transition: color 0.15s; }
.foot-links span:hover { color: var(--primary); }

/* Responsive */
@media (max-width: 1280px) {
  .main-columns { grid-template-columns: 1fr; }
  .right-col { position: static; }
  .similar-grid { grid-template-columns: repeat(3, 1fr); }
  .cast-grid { grid-template-columns: repeat(5, 1fr); }
  .hero-inner { flex-direction: column; gap: 32px; }
  .hero-poster-card { width: 100%; max-width: 400px; }
  .hero-title { font-size: 48px; }
}
@media (max-width: 768px) {
  .sidebar { display: none; }
  .similar-grid { grid-template-columns: repeat(2, 1fr); }
  .cast-grid { grid-template-columns: repeat(4, 1fr); }
  .stills-grid { grid-template-columns: repeat(2, 1fr); }
  .hero-title { font-size: 36px; }
  .rating-big { font-size: 56px; }
  .rating-sources { padding-left: 16px; gap: 14px; }
}
</style>
