"""First photo of every product -> app/src/main/assets/img/<id>.webp (square, white, 480 px).

Bundled in the APK so the catalog shows pictures offline and does not depend on sanjet.ru
being reachable from the phone's WebView. Existing files are kept; delete one to rebuild it.
Usage: python3 -I tools/thumbs.py app/src/main/assets/products.js app/src/main/assets/img
"""
import io, json, os, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from PIL import Image

SIZE = 480


def thumb(data):
    im = Image.open(io.BytesIO(data))
    im = im.convert('RGBA') if im.mode in ('P', 'LA', 'RGBA') else im.convert('RGB')
    im.thumbnail((SIZE - 24, SIZE - 24), Image.LANCZOS)
    out = Image.new('RGB', (SIZE, SIZE), 'white')
    out.paste(im, ((SIZE - im.width) // 2, (SIZE - im.height) // 2), im if im.mode == 'RGBA' else None)
    buf = io.BytesIO()
    out.save(buf, 'WEBP', quality=72, method=6)
    return buf.getvalue()


def one(p, folder):
    path = os.path.join(folder, p['id'] + '.webp')
    if os.path.exists(path) or not p['photos']:
        return 'skip'
    try:
        with urllib.request.urlopen(p['photos'][0], timeout=60) as r:
            data = thumb(r.read())
        with open(path, 'wb') as f:
            f.write(data)
        return 'ok'
    except Exception as e:
        return f'fail {p["id"]}: {e}'


if __name__ == '__main__':
    src, folder = sys.argv[1], sys.argv[2]
    text = open(src, encoding='utf-8').read()
    items = json.loads(text[text.index('['):text.rindex(']') + 1])
    os.makedirs(folder, exist_ok=True)
    with ThreadPoolExecutor(8) as ex:
        res = list(ex.map(lambda p: one(p, folder), items))
    fails = [r for r in res if r.startswith('fail')]
    print(res.count('ok'), 'new,', res.count('skip'), 'skipped,', len(fails), 'failed')
    print('\n'.join(fails[:20]))
