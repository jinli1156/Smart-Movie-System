"""Copy the reviewed permission/UI changes while preserving uniCloud adapters."""
from pathlib import Path
import shutil

cloud = Path(__file__).resolve().parents[1]
root = cloud.parent
files = {
    'movie-ticket-admin': [
        'api/order.ts', 'api/recommend.ts', 'components/BatchActions.vue',
        'layout/MainLayout.vue', 'router/index.ts', 'types/auth.ts', 'types/index.ts',
        'views/Cinema.vue', 'views/Dashboard.vue', 'views/Order.vue', 'views/User.vue',
        'views/RecommendationEvaluation.vue',
    ],
    'movie-ticket-user': [
        'components/AppSidebar.vue', 'views/CinemaDetail.vue', 'views/Cinemas.vue',
        'views/Home.vue', 'views/MovieDetail.vue', 'views/Movies.vue',
        'views/OrderConfirm.vue', 'views/OrderPay.vue', 'views/Profile.vue', 'views/SeatSelection.vue',
    ],
}
for app, paths in files.items():
    for relative in paths:
        text = (root / app / 'src' / relative).read_text(encoding='utf-8')
        if app == 'movie-ticket-admin' and relative == 'router/index.ts':
            text = text.replace('createWebHistory', 'createWebHashHistory')
            text = text.replace('createWebHashHistory()', 'createWebHashHistory(import.meta.env.BASE_URL)')
        if app == 'movie-ticket-admin' and relative == 'views/Cinema.vue':
            text = text.replace("const IMAGE_BASE = '/api/uploads/'", "const IMAGE_BASE = '/uploads/'")
            text = text.replace('optimizedFile.size >= 5 * 1024 * 1024', 'optimizedFile.size >= 1400 * 1024')
            text = text.replace('压缩后图片仍超过5MB', '压缩后图片仍超过 1.4MB')
        if relative == 'views/RecommendationEvaluation.vue':
            text = text.replace('在相同时间切分和候选集下，对比混合推荐、基线与消融实验。',
                                '对比云端规则推荐、评分基线与消融实验，评估协议以本次接口结果为准。')
            text = text.replace("name: '完整混合推荐', description: '时间衰减、内容与行为、场景、趋势及多样性重排'",
                                "name: '当前云端规则推荐', description: '类型偏好、评分与行为信号的加权排序；与 Spring Boot 混合算法不同'")
        target = cloud / 'frontend' / app / 'src' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')
print(f'Synced {sum(map(len, files.values()))} reviewed files; cloud routes, API and upload adapters preserved.')
