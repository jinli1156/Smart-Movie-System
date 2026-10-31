"""Move the existing upcoming releases panel beside the hero and remove ticket polling."""
from pathlib import Path
cloud=Path(__file__).resolve().parent.parent
for app in [cloud.parent/'movie-ticket-user',cloud/'frontend/movie-ticket-user']:
    file=app/'src/views/Home.vue'
    value=file.read_text(encoding='utf-8')
    if 'const todayBoxOfficeRanking' not in value:
        continue
    begin=value.index('            <!-- Coming Soon -->')
    end=value.index('            <!-- AI Personalization Card -->',begin)
    block=value[begin:end]
    panel=block[block.index('            <div class="cs-panel">'):].rstrip()
    panel=panel.replace('<div class="cs-panel">','<aside class="hero-side cs-panel" aria-label="即将上映" v-loading="loadingComing">',1)
    panel=panel[:panel.rfind('</div>')]+'</aside>'
    panel=panel.replace('                <span class="cs-want">想看</span>\n','')
    panel=panel.replace('class="cs-item"','class="cs-item" role="button" tabindex="0" @keydown.enter="goToDetail(m.id)" @keydown.space.prevent="goToDetail(m.id)"')
    value=value[:begin]+value[end:]
    begin=value.index('          <aside class="hero-side" aria-label="今日票房排行榜">')
    end=value.index('          </aside>',begin)+len('          </aside>')
    value=value[:begin]+panel+value[end:]
    value=value.replace('v-if="heroMovie"\n          @mouseenter','v-if="heroMovie || comingSoon.length || loadingComing"\n          @mouseenter').replace('<div class="hero-main">','<div class="hero-main" v-if="heroMovie">',1)
    value=value.replace('Movie, Cinema, MovieBoxOfficeRank,','Movie, Cinema,')
    begin=value.index('const todayBoxOfficeRanking =')
    end=value.index('let homeDisposed =',begin)
    value=value[:begin]+value[end:]
    begin=value.index('const upNextPalette =')
    end=value.index('const csGradients =',begin)
    value=value[:begin]+value[end:]
    begin=value.index('const formatBoxOffice =')
    end=value.index('const dayOf =',begin)
    value=value[:begin]+value[end:]
    begin=value.index('const pauseBoxOfficeRefresh =')
    end=value.index('/** 按当前偏好',begin)
    value=value[:begin]+value[end:]
    for line in ["  document.addEventListener('visibilitychange', resumeBoxOfficeRefresh)\n","  window.addEventListener('online', resumeBoxOfficeRefresh)\n","  void loadTodayBoxOfficeRanking()\n","  pauseBoxOfficeRefresh()\n","  document.removeEventListener('visibilitychange', resumeBoxOfficeRefresh)\n","  window.removeEventListener('online', resumeBoxOfficeRefresh)\n"]:
        value=value.replace(line,'')
    value=value.replace('  .hero-side { display: none; }','  .hero { flex-direction: column; height: auto; }\n  .hero-main { min-height: 460px; }\n  .hero-side { width: 100%; }')
    value='\n'.join(line for line in value.split('\n') if not line.startswith('.box-office-'))
    file.write_text(value,encoding='utf-8')
    print('Replaced homepage box office with upcoming releases:',app.name)
