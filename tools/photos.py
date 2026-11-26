"""All product photos -> one zip the app downloads once after install (Settings → «Скачать фото»).

Entries are flat names <product id>-<photo index>.webp (1200 px, WebP), so the app can unpack them
without trusting paths from the archive. Photos that fail to download are skipped; the app hides them.
Usage: python3 -I tools/photos.py app/src/main/assets/products.js photos-<ver>.zip
"""
import io, json, sys, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor
from PIL import Image

SIZE = 1200


def webp(data):
    im = Image.open(io.BytesIO(data))
    if im.mode in ('P', 'LA', 'RGBA'):  # flatten transparency onto white, as the photos are shown on white
        im = im.convert('RGBA'); bg = Image.new('RGB', im.size, 'white'); bg.paste(im, mask=im.split()[3]); im = bg
    im = im.convert('RGB')
    im.thumbnail((SIZE, SIZE), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, 'WEBP', quality=74, method=4)
    return buf.getvalue()


def fetch(job):
    name, url = job
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return name, webp(r.read())
        except Exception as e:
            err = e
    return name, err


if __name__ == '__main__':
    assert webp(b'GIF89a\x01\x00\x01\x00\x80\x00\x00\xff\xff\xff\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;')[:4] == b'RIFF'
    text = open(sys.argv[1], encoding='utf-8').read()
    items = json.loads(text[text.index('['):text.index('];') + 1])
    jobs = [(f"{p['id']}-{k}.webp", u) for p in items for k, u in enumerate(p['photos'])]
    ok = failed = 0
    with zipfile.ZipFile(sys.argv[2], 'w', zipfile.ZIP_STORED) as z, ThreadPoolExecutor(12) as ex:
        for name, res in ex.map(fetch, jobs):
            if isinstance(res, bytes):
                z.writestr(name, res); ok += 1
            else:
                failed += 1; print('skip', name, res)
    print(ok, 'photos packed,', failed, 'skipped')
    assert ok > len(jobs) * 0.9, 'too many photos failed: refusing to publish'
