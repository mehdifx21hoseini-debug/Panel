"""Turns rendered frames (build/frames/<key>/) into the looping videos the app plays:
app/src/main/assets/exercise_anim/<key>_light.mp4 and <key>_dark.mp4, each frame laid on the
app's stage colour for that theme. Usage: python3 encode.py [key ...]"""
import os, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '../../app/src/main/assets/exercise_anim')
STAGES = {'light': (0xEE, 0xF2, 0xF9), 'dark': (0x16, 0x22, 0x4A)}  # FitColors.surfaceContainer
FPS = 30

def encode(key):
    src = os.path.join(HERE, 'build/frames', key)
    files = sorted(f for f in os.listdir(src) if f.endswith('.png'))
    if not files: return
    newest = max(os.path.getmtime(os.path.join(src, f)) for f in files)
    outs = [os.path.join(OUT, f'{key}_{t}.mp4') for t in STAGES]
    if all(os.path.exists(o) and os.path.getmtime(o) > newest for o in outs): return  # already up to date
    frames = [Image.open(os.path.join(src, f)).convert('RGBA') for f in files]
    w, h = frames[0].size
    for theme, rgb in STAGES.items():
        out = os.path.join(OUT, f'{key}_{theme}.mp4')
        p = subprocess.Popen(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{w}x{h}', '-r', str(FPS), '-i', '-',
                              '-c:v', 'libx264', '-profile:v', 'main', '-preset', 'veryslow', '-crf', '25', '-pix_fmt', 'yuv420p',
                              '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-movflags', '+faststart', '-an', out], stdin=subprocess.PIPE)
        for f in frames:
            stage = Image.new('RGBA', (w, h), rgb + (255,)); stage.alpha_composite(f)
            p.stdin.write(stage.convert('RGB').tobytes())
        p.stdin.close(); p.wait()
        if p.returncode: sys.exit(f'ffmpeg failed for {key} {theme}')
    print(key, ' '.join(f"{t}:{os.path.getsize(os.path.join(OUT, f'{key}_{t}.mp4')) // 1024}KB" for t in STAGES))

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    keys = sys.argv[1:] or sorted(os.listdir(os.path.join(HERE, 'build/frames')))
    for k in keys: encode(k)
