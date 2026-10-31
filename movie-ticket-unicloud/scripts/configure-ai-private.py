"""Merge existing private model configuration into server-only cloud credentials."""
from pathlib import Path
import json, secrets

root = Path(__file__).resolve().parent.parent.parent
file = root / 'movie-ticket-unicloud/movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/secrets.json'
values = json.loads(file.read_text(encoding='utf-8'))
local = {}
by_file = {}
for name in ['assistant.properties', 'marketing.properties', 'qwen.properties']:
    by_file[name] = {}
    for line in (root / '.local' / name).read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            local[key.strip()] = value.strip()
            by_file[name][key.strip()] = value.strip()
for dest, source in {
    'DEEPSEEK_API_KEY': 'DEEPSEEK_API_KEY', 'AI_IMAGE_KEY': 'marketing.image-api-key',
    'AI_IMAGE_PROVIDER': 'marketing.image-provider', 'AI_IMAGE_MODEL': 'marketing.image-model',
    'AI_IMAGE_BASE': 'marketing.image-base-url',
    'AI_VISION_KEY': 'marketing.zhipu-api-key',
}.items():
    if local.get(source):
        values[dest] = local[source]
if not values.get('AI_ASSET_KEY'):
    values['AI_ASSET_KEY'] = secrets.token_hex(32)
values.setdefault('AI_VISION_BASE', 'https://open.bigmodel.cn/api/paas/v4')
values.setdefault('AI_VISION_MODEL', 'glm-4.6v-flash')
zhipu=by_file['marketing.properties']
values['AI_TEXT_IMAGE_KEY']=zhipu.get('marketing.image-api-key') or zhipu.get('marketing.zhipu-api-key') or values.get('AI_TEXT_IMAGE_KEY')
values['AI_TEXT_IMAGE_BASE']=zhipu.get('marketing.image-base-url') or 'https://open.bigmodel.cn/api/paas/v4'
values['AI_TEXT_IMAGE_MODEL']='glm-image'
assert values.get('JWT_SECRET') and values.get('AI_IMAGE_KEY') and values.get('DEEPSEEK_API_KEY')
assert values.get('AI_TEXT_IMAGE_KEY') and values['AI_TEXT_IMAGE_BASE'].startswith('https://open.bigmodel.cn/')
file.write_text(json.dumps(values, indent=2) + '\n', encoding='utf-8')
print('Server-only credentials configured; text-to-image: glm-image; image-to-image: qwen-image-3.0')
