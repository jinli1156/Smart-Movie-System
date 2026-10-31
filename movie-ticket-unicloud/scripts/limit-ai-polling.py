"""Reduce cloud API calls without changing durable job execution semantics."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent / 'frontend'
changes = {
    'movie-ticket-user/src/views/PosterStudio.vue': [
        ('window.setTimeout(refresh, 3000)', "window.setTimeout(() => { if (document.visibilityState === 'visible') void refresh(true); else schedulePoll() }, 8000)"),
        ('async function refresh() {', 'async function refresh(polling?: unknown) {'),
        ('Promise.all([posterApi.capabilities(), posterApi.list()])', 'Promise.all([polling === true ? Promise.resolve(capabilities.value) : posterApi.capabilities(), posterApi.list()])'),
    ],
    'movie-ticket-user/src/components/SouvenirTicketDialog.vue': [
        ('timer = setTimeout(async () => {\n    try {', "timer = setTimeout(async () => {\n    if (document.visibilityState !== 'visible') { schedulePoll(expected); return }\n    try {"),
        ('  }, 4000)', '  }, 8000)'),
    ],
    'movie-ticket-user/src/components/MarketingPlacements.vue': [
        ('}, 30000)', '}, 300000)'),
    ],
    'movie-ticket-admin/src/components/MarketingWorkspace.vue': [
        ("timer = setInterval(() => { if (!disposed) void refresh().catch(() => {}) }, 2500)",
         "timer = setInterval(() => { if (!disposed && document.visibilityState === 'visible' && detail.value?.tasks.some(task => ['QUEUED', 'RUNNING', 'PLANNING', 'SUBMITTING', 'PERSISTING'].includes(task.status))) void refresh().catch(() => {}) }, 8000)"),
    ],
}
for name, replacements in changes.items():
    file = root / name
    value = file.read_text(encoding='utf-8')
    for before, after in replacements:
        value = value.replace(before, after)
    file.write_text(value, encoding='utf-8')
print('Cloud polling limited: active visible tasks only; no repeated capability calls.')
