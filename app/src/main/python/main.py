import json
import os
import hashlib
import time
import socket
import io
from Crypto.Cipher import AES
from Crypto.Protocol.KDF import PBKDF2
from Crypto.Random import get_random_bytes
from flask import Flask, send_file, request, jsonify, render_template_string

app = Flask(__name__)

STORAGE_DIR = ""
NOMEDIA_DIR = ""
META_FILE = ""
SALT = b"pv_salt_v1"
PBKDF2_ITER = 200000
CURRENT_KEY = None
IS_DECOY_SESSION = False

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"

WEB_LOGIN_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>PhotoVault - Secure Login</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
    <style>
        body {
            font-family: 'Outfit', sans-serif;
            background-color: #0A0A0B;
            color: #E4E4E7;
            margin: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
        }
        .login-card {
            background: rgba(255, 255, 255, 0.02);
            border: 1px solid rgba(255, 255, 255, 0.05);
            padding: 40px;
            border-radius: 24px;
            width: 100%;
            max-width: 400px;
            text-align: center;
            box-shadow: 0 20px 50px rgba(0,0,0,0.5);
            backdrop-filter: blur(20px);
        }
        h2 {
            margin-top: 0;
            font-weight: 600;
            background: linear-gradient(135deg, #FFF 30%, #A1A1AA 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .pin-input {
            width: 100%;
            background: rgba(255,255,255,0.03);
            border: 1px solid rgba(255,255,255,0.08);
            border-radius: 12px;
            color: #FFF;
            font-size: 24px;
            letter-spacing: 8px;
            text-align: center;
            padding: 12px;
            margin-bottom: 20px;
            outline: none;
            transition: all 0.3s ease;
        }
        .pin-input:focus {
            border-color: rgba(255,255,255,0.3);
            box-shadow: 0 0 15px rgba(255,255,255,0.05);
        }
        .btn-submit {
            width: 100%;
            background: #FFF;
            color: #0A0A0B;
            border: none;
            padding: 14px;
            border-radius: 12px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        .btn-submit:hover {
            opacity: 0.9;
            transform: translateY(-1px);
        }
        .error-msg {
            color: #EF4444;
            font-size: 14px;
            margin-top: 10px;
            display: none;
        }
    </style>
</head>
<body>
    <div class="login-card">
        <div style="font-size: 48px; margin-bottom: 16px;">🛡️</div>
        <h2>PhotoVault Portal</h2>
        <p style="color: #71717A; font-size: 14px; margin-bottom: 30px;">Enter your vault PIN to unlock this session.</p>
        <input type="password" maxlength="4" class="pin-input" id="pin-field" placeholder="••••">
        <button class="btn-submit" onclick="submitPin()">Unlock Vault</button>
        <div class="error-msg" id="error-field">Invalid PIN. Please try again.</div>
    </div>
    <script>
        function submitPin() {
            const pin = document.getElementById('pin-field').value;
            const error = document.getElementById('error-field');
            error.style.display = 'none';
            
            const formData = new FormData();
            formData.append('pin', pin);
            
            fetch('/web_login', {
                method: 'POST',
                body: formData
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    window.location.reload();
                } else {
                    error.style.display = 'block';
                    document.getElementById('pin-field').value = '';
                }
            })
            .catch(() => {
                error.style.display = 'block';
            });
        }
        document.getElementById('pin-field').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') submitPin();
        });
    </script>
</body>
</html>
"""

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Vault</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; }
        body {
            font-family: 'Outfit', sans-serif;
            background-color: #0A0A0B;
            color: #E4E4E7;
            margin: 0;
            padding: 0;
            -webkit-tap-highlight-color: transparent;
        }
        .app-container {
            max-width: 600px;
            margin: 0 auto;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            background: #0A0A0B;
        }
        header {
            position: sticky;
            top: 0;
            z-index: 100;
            background: rgba(10, 10, 11, 0.85);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            padding: 20px 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        h1 {
            font-size: 22px;
            font-weight: 600;
            letter-spacing: -0.5px;
            margin: 0;
            background: linear-gradient(135deg, #FFF 30%, #A1A1AA 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .header-actions {
            display: flex;
            gap: 4px;
        }
        button {
            font-family: inherit;
            cursor: pointer;
            border: none;
            outline: none;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .btn-tab {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid rgba(255, 255, 255, 0.05);
            color: #A1A1AA;
            padding: 6px 10px;
            border-radius: 99px;
            font-size: 12px;
            font-weight: 500;
        }
        .btn-tab.active {
            background: #FFFFFF;
            color: #0A0A0B;
            border-color: #FFFFFF;
            box-shadow: 0 4px 12px rgba(255, 255, 255, 0.1);
        }
        .btn-add {
            background: rgba(255, 255, 255, 0.08);
            color: #FFFFFF;
            width: 38px;
            height: 38px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            font-weight: 300;
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .btn-add:active {
            transform: scale(0.95);
            background: rgba(255, 255, 255, 0.15);
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 12px;
            padding: 24px;
        }
        .img-card {
            position: relative;
            aspect-ratio: 1;
            border-radius: 12px;
            overflow: hidden;
            background: rgba(255, 255, 255, 0.02);
            border: 1px solid rgba(255, 255, 255, 0.04);
            animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
        }
        .img-card img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            transition: transform 0.3s ease;
        }
        .img-card:active img {
            transform: scale(1.05);
        }
        .del-btn {
            position: absolute;
            top: 8px;
            right: 8px;
            background: rgba(10, 10, 11, 0.75);
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
            color: #EF4444;
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 50%;
            width: 26px;
            height: 26px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            font-weight: 400;
        }
        .del-btn:active {
            transform: scale(0.9);
            background: rgba(239, 68, 68, 0.2);
            color: #EF4444;
        }
        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            flex: 1;
            padding: 40px;
            text-align: center;
            color: #71717A;
            animation: fadeIn 0.5s ease;
        }
        .empty-icon {
            font-size: 40px;
            margin-bottom: 16px;
            opacity: 0.4;
        }
        .empty-text {
            font-size: 14px;
            font-weight: 400;
            letter-spacing: -0.1px;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: scale(0.96); }
            to { opacity: 1; transform: scale(1); }
        }
        .settings-view {
            display: flex;
            flex-direction: column;
            gap: 20px;
            padding: 24px;
            flex: 1;
        }
        .settings-card {
            background: rgba(255, 255, 255, 0.02);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 16px;
            padding: 20px;
        }
    </style>
</head>
<body>
    <div class="app-container">
        <header>
            <h1 id="view-title">Vault</h1>
            <div class="header-actions" style="display:flex; align-items:center; gap:4px;">
                <button class="btn-tab active" id="gallery-btn" onclick="switchView('gallery')">Gallery</button>
                {% if not is_decoy %}
                <button class="btn-tab" id="intruders-btn" onclick="switchView('intruders')">Logs</button>
                {% endif %}
                <button class="btn-tab" id="browser-btn" onclick="window.android.openBrowser()">Web</button>
                <button class="btn-tab" id="settings-btn" onclick="switchView('settings')">⚙️</button>
                <button class="btn-add" id="add-btn" onclick="triggerUpload()">+</button>
            </div>
        </header>
        
        <div id="image-grid" class="grid"></div>
        
        <!-- Settings View -->
        <div id="settings-view" class="settings-view" style="display:none;">
            <!-- Web Dashboard Card -->
            <div class="settings-card">
                <div style="display:flex; align-items:center; gap:12px; margin-bottom:12px;">
                    <div style="font-size:24px;">🌐</div>
                    <div>
                        <div style="font-weight:600; font-size:16px;">Web Dashboard</div>
                        <div style="font-size:12px; color:#71717A;">Manage vault from PC/Tablet</div>
                    </div>
                </div>
                <div style="background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.05); border-radius:10px; padding:12px; font-family:monospace; font-size:14px; color:#A1A1AA; word-break:break-all; text-align:center;">
                    http://{{ local_ip }}:5000/
                </div>
                <div style="font-size:11px; color:#71717A; margin-top:8px; text-align:center;">
                    Devices must be connected to the same Wi-Fi network.
                </div>
            </div>

            <!-- Decoy PIN Card (hidden in decoy session!) -->
            {% if not is_decoy %}
            <div class="settings-card">
                <div style="display:flex; align-items:center; gap:12px; margin-bottom:12px;">
                    <div style="font-size:24px;">🎭</div>
                    <div>
                        <div style="font-weight:600; font-size:16px;">Decoy PIN Settings</div>
                        <div style="font-size:12px; color:#71717A;">Shows secondary empty vault</div>
                    </div>
                </div>
                <div style="display:flex; gap:10px;">
                    <input type="password" id="decoy-pin-input" maxlength="4" placeholder="New 4-digit PIN" style="flex:1; background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.08); border-radius:10px; color:#FFF; padding:10px; font-size:14px; text-align:center; outline:none;">
                    <button onclick="updateDecoyPin()" style="background:#FFF; color:#0A0A0B; font-weight:600; padding:10px 16px; border-radius:10px; font-size:13px;">Save</button>
                </div>
                <div style="font-size:11px; color:#71717A; margin-top:8px;">
                    Enter this PIN on the calculator to open the decoy sandbox vault.
                </div>
            </div>
            {% else %}
            <!-- Decoy Badge -->
            <div style="background:rgba(239,68,68,0.05); border:1px solid rgba(239,68,68,0.15); border-radius:16px; padding:20px; text-align:center;">
                <div style="font-size:24px; margin-bottom:8px;">🔒</div>
                <div style="font-weight:600; font-size:16px; color:#EF4444;">Decoy Session Mode</div>
                <div style="font-size:12px; color:#71717A; margin-top:4px;">
                    You are in the decoy sandbox. Real settings are locked.
                </div>
            </div>
            {% endif %}

            <!-- Google Drive Cloud Backup Card -->
            <div class="settings-card">
                <div style="display:flex; align-items:center; gap:12px; margin-bottom:12px;">
                    <div style="font-size:24px;">☁️</div>
                    <div>
                        <div style="font-weight:600; font-size:16px;">Google Drive Backup</div>
                        <div style="font-size:12px; color:#71717A;">Keep files secure in the cloud</div>
                    </div>
                </div>
                <div id="backup-status-text" style="font-size:13px; color:#A1A1AA; margin-bottom:12px;">
                    Status: Checking...
                </div>
                <button onclick="window.android.connectGoogleDrive()" style="background:#4285F4; color:#FFF; font-weight:600; padding:10px 16px; border-radius:10px; font-size:13px; width:100%; border:none; outline:none; text-align:center; cursor:pointer;">
                    Connect Google Drive
                </button>
            </div>

            <!-- Lock Button -->
            <button onclick="lockVault()" style="background:rgba(239,68,68,0.1); border:1px solid rgba(239,68,68,0.2); color:#EF4444; width:100%; padding:14px; border-radius:12px; font-weight:600; font-size:15px; margin-top:auto;">
                Lock Vault
            </button>
        </div>
        
        <div id="empty-state" class="empty-state" style="display:none;">
            <div id="empty-icon-container" class="empty-icon">🛡️</div>
            <div class="empty-text" id="empty-text">Your vault is empty</div>
        </div>
    </div>
    
    <input type="file" id="web-file-input" style="display:none;" accept="image/*" onchange="uploadFile(this)">

    <script>
        let currentView = 'gallery';
        const isDecoy = {{ 'true' if is_decoy else 'false' }};
        
        // Hide Browser button on PC
        if (!window.android || !window.android.openBrowser) {
            const browserBtn = document.getElementById('browser-btn');
            if (browserBtn) browserBtn.style.display = 'none';
        }

        function triggerUpload() {
            if (window.android && window.android.pickImage) {
                window.android.pickImage();
            } else {
                document.getElementById('web-file-input').click();
            }
        }

        function uploadFile(input) {
            if (input.files && input.files[0]) {
                const file = input.files[0];
                const formData = new FormData();
                formData.append('image', file);
                
                fetch('/web_upload', {
                    method: 'POST',
                    body: formData
                })
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        loadImages();
                    } else {
                        alert('Upload failed: ' + (data.error || 'Unknown error'));
                    }
                })
                .catch(() => {
                    alert('Upload failed due to connection error.');
                });
            }
        }

        function loadImages() {
            const url = currentView === 'gallery' ? '/list_images' : '/list_intruders';
            fetch(url).then(res => res.json()).then(data => {
                const grid = document.getElementById('image-grid');
                const empty = document.getElementById('empty-state');
                const emptyText = document.getElementById('empty-text');
                
                if (!data.images || data.images.length === 0) {
                    grid.innerHTML = '';
                    if (currentView === 'gallery') {
                        document.getElementById('empty-icon-container').innerHTML = '<img src="/poster.png" style="width: 85%; max-width: 320px; border-radius: 16px; margin-bottom: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.05);">';
                        emptyText.innerText = 'Your vault is empty';
                    } else {
                        document.getElementById('empty-icon-container').innerHTML = '🛡️';
                        emptyText.innerText = 'No intruder events detected';
                    }
                    empty.style.display = 'flex';
                    return;
                }
                empty.style.display = 'none';
                let html = '';
                data.images.forEach((img, idx) => {
                    const srcUrl = currentView === 'gallery' ? `/image/${img}` : `/intruder/${img}`;
                    const delFunc = currentView === 'gallery' ? `deleteImg('${img}')` : `deleteIntruder('${img}')`;
                    const isVideo = img.toLowerCase().endsWith('.mp4');
                    const mediaElement = isVideo 
                        ? `<video src="${srcUrl}" style="width:100%; height:100%; object-fit:cover;" onclick="this.paused ? this.play() : this.pause()"></video><div style="position:absolute; top:50%; left:50%; transform:translate(-50%,-50%); font-size:24px; pointer-events:none; opacity:0.8;">▶️</div>`
                        : `<img src="${srcUrl}">`;
                    html += `
                        <div class="img-card" style="animation-delay: ${idx * 0.05}s">
                            ${mediaElement}
                            <button class="del-btn" onclick="${delFunc}">×</button>
                        </div>`;
                });
                grid.innerHTML = html;
            });
        }

        function switchView(view) {
            currentView = view;
            const galleryBtn = document.getElementById('gallery-btn');
            const intrudersBtn = document.getElementById('intruders-btn');
            const settingsBtn = document.getElementById('settings-btn');
            const addBtn = document.getElementById('add-btn');
            const title = document.getElementById('view-title');
            const grid = document.getElementById('image-grid');
            const settingsView = document.getElementById('settings-view');
            
            galleryBtn.classList.remove('active');
            if (intrudersBtn) intrudersBtn.classList.remove('active');
            settingsBtn.classList.remove('active');
            
            if (view === 'gallery') {
                galleryBtn.classList.add('active');
                addBtn.style.visibility = 'visible';
                title.innerText = 'Vault';
                grid.style.display = 'grid';
                settingsView.style.display = 'none';
                loadImages();
            } else if (view === 'intruders') {
                if (intrudersBtn) intrudersBtn.classList.add('active');
                addBtn.style.visibility = 'hidden';
                title.innerText = 'Logs';
                grid.style.display = 'grid';
                settingsView.style.display = 'none';
                loadImages();
            } else if (view === 'settings') {
                settingsBtn.classList.add('active');
                addBtn.style.visibility = 'hidden';
                title.innerText = 'Settings';
                grid.style.display = 'none';
                settingsView.style.display = 'flex';
                document.getElementById('empty-state').style.display = 'none';
                
                // Query backup status
                if (window.android && window.android.getBackupStatus) {
                    document.getElementById('backup-status-text').innerText = "Status: " + window.android.getBackupStatus();
                }
            }
        }

        function deleteImg(name) {
            if(confirm('Permanently delete this item?')) {
                fetch('/delete/' + name).then(() => loadImages());
            }
        }

        function deleteIntruder(name) {
            if(confirm('Delete this intruder log?')) {
                fetch('/delete_intruder/' + name).then(() => loadImages());
            }
        }

        function updateDecoyPin() {
            const pin = document.getElementById('decoy-pin-input').value;
            if (!/^\\d{4}$/.test(pin)) {
                alert('Decoy PIN must be exactly 4 digits');
                return;
            }
            const formData = new FormData();
            formData.append('decoy_pin', pin);
            fetch('/set_decoy_pin', {
                method: 'POST',
                body: formData
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    alert('Decoy PIN updated successfully');
                    document.getElementById('decoy-pin-input').value = '';
                } else {
                    alert('Failed: ' + data.error);
                }
            })
            .catch(() => alert('Connection error'));
        }

        function lockVault() {
            fetch('/lock').then(() => {
                if (window.android && window.android.lockApp) {
                    window.android.lockApp();
                } else {
                    window.location.reload();
                }
            });
        }

        window.onload = loadImages;
    </script>
</body>
</html>
"""

def init_vault(base_path):
    global STORAGE_DIR, NOMEDIA_DIR, META_FILE
    STORAGE_DIR = base_path
    NOMEDIA_DIR = os.path.join(STORAGE_DIR, ".nomedia")
    META_FILE = os.path.join(STORAGE_DIR, "meta.json")
    if not os.path.exists(NOMEDIA_DIR):
        os.makedirs(NOMEDIA_DIR, exist_ok=True)
    if not os.path.exists(META_FILE):
        with open(META_FILE, "w") as f:
            json.dump({}, f)

def get_current_nomedia_dir():
    if IS_DECOY_SESSION:
        decoy_dir = os.path.join(STORAGE_DIR, ".nomedia_decoy")
        if not os.path.exists(decoy_dir):
            os.makedirs(decoy_dir, exist_ok=True)
        return decoy_dir
    return NOMEDIA_DIR

def derive_key(pin):
    return PBKDF2(pin, SALT, dkLen=32, count=PBKDF2_ITER)

def encrypt_bytes(key, data):
    if not isinstance(data, (bytes, bytearray)):
        data = bytes(data)
    iv = get_random_bytes(16)
    cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
    ciphertext, tag = cipher.encrypt_and_digest(data)
    return iv + tag + ciphertext

def decrypt_bytes(key, blob):
    iv = blob[:16]
    tag = blob[16:32]
    ciphertext = blob[32:]
    cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
    return cipher.decrypt_and_verify(ciphertext, tag)

def is_unlocked():
    return CURRENT_KEY is not None

def is_pin_set():
    if not os.path.exists(META_FILE): return False
    try:
        with open(META_FILE, "r") as f:
            data = json.load(f)
        return "pin_hash" in data
    except:
        return False

def register_pin(pin):
    global CURRENT_KEY, IS_DECOY_SESSION
    if not os.path.exists(META_FILE): return False
    try:
        pin_hash = hashlib.sha256(pin.encode() + SALT).hexdigest()
        default_decoy = "9999" if pin == "0000" else "0000"
        decoy_hash = hashlib.sha256(default_decoy.encode() + SALT).hexdigest()
        
        with open(META_FILE, "r") as f:
            data = json.load(f)
        data["pin_hash"] = pin_hash
        if "decoy_hash" not in data:
            data["decoy_hash"] = decoy_hash
        with open(META_FILE, "w") as f:
            json.dump(data, f)
        
        CURRENT_KEY = derive_key(pin)
        IS_DECOY_SESSION = False
        return True
    except:
        return False

def verify_pin(pin):
    global CURRENT_KEY, IS_DECOY_SESSION
    if not is_pin_set():
        return False
    try:
        with open(META_FILE, "r") as f:
            data = json.load(f)
        pin_hash = hashlib.sha256(pin.encode() + SALT).hexdigest()
        
        if data["pin_hash"] == pin_hash:
            CURRENT_KEY = derive_key(pin)
            IS_DECOY_SESSION = False
            return True
            
        decoy_hash = data.get("decoy_hash")
        if decoy_hash and decoy_hash == pin_hash:
            CURRENT_KEY = derive_key(pin)
            IS_DECOY_SESSION = True
            return True
    except Exception as e:
        print("verify_pin error:", e)
    return False

def list_images():
    current_dir = get_current_nomedia_dir()
    if not os.path.exists(current_dir): return []
    return [f for f in os.listdir(current_dir) if not f.startswith(".") and not f.startswith("security_")]

def add_image(filename, img_bytes):
    if not CURRENT_KEY: return False
    blob = encrypt_bytes(CURRENT_KEY, img_bytes)
    current_dir = get_current_nomedia_dir()
    with open(os.path.join(current_dir, filename), "wb") as f:
        f.write(blob)
    return True

def delete_image(filename):
    current_dir = get_current_nomedia_dir()
    filepath = os.path.join(current_dir, filename)
    if os.path.exists(filepath): os.remove(filepath)
    return True

@app.route("/")
def index():
    if not CURRENT_KEY:
        return render_template_string(WEB_LOGIN_TEMPLATE)
    return render_template_string(HTML_TEMPLATE, is_decoy=IS_DECOY_SESSION, local_ip=get_local_ip())

@app.route("/web_login", methods=["POST"])
def web_login():
    pin = request.form.get("pin", "")
    if verify_pin(pin):
        return jsonify({"success": True})
    return jsonify({"success": False, "error": "Invalid PIN"})

@app.route("/web_upload", methods=["POST"])
def web_upload():
    if not CURRENT_KEY: return jsonify({"success": False, "error": "Unauthorized"}), 401
    if 'image' not in request.files:
        return jsonify({"success": False, "error": "No file uploaded"})
    
    file = request.files['image']
    if file.filename == '':
        return jsonify({"success": False, "error": "No file selected"})
        
    try:
        img_bytes = file.read()
        filename = "img_" + str(int(time.time() * 1000)) + ".jpg"
        if add_image(filename, img_bytes):
            return jsonify({"success": True})
        return jsonify({"success": False, "error": "Encryption failed"})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route("/set_decoy_pin", methods=["POST"])
def set_decoy_pin():
    if not CURRENT_KEY or IS_DECOY_SESSION: return jsonify({"success": False, "error": "Unauthorized"}), 401
    decoy_pin = request.form.get("decoy_pin", "")
    if not decoy_pin.isdigit() or len(decoy_pin) != 4:
        return jsonify({"success": False, "error": "Decoy PIN must be exactly 4 digits"})
    
    try:
        with open(META_FILE, "r") as f:
            data = json.load(f)
        decoy_hash = hashlib.sha256(decoy_pin.encode() + SALT).hexdigest()
        data["decoy_hash"] = decoy_hash
        with open(META_FILE, "w") as f:
            json.dump(data, f)
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route("/list_images")
def get_list():
    if not CURRENT_KEY: return jsonify({"images": []}), 401
    return jsonify({"images": list_images()})

@app.route("/image/<filename>")
def serve_image(filename):
    if not CURRENT_KEY: return "Unauthorized", 401
    current_dir = get_current_nomedia_dir()
    filepath = os.path.join(current_dir, filename)
    try:
        with open(filepath, "rb") as f:
            blob = f.read()
        plain = decrypt_bytes(CURRENT_KEY, blob)
        return send_file(io.BytesIO(plain), mimetype='image/jpeg')
    except:
        return "Decryption Failed", 500

@app.route("/delete/<filename>")
def delete_route(filename):
    if not CURRENT_KEY: return "Unauthorized", 401
    delete_image(filename)
    return jsonify({"status": "deleted"})

@app.route("/poster.png")
def serve_poster():
    dirname = os.path.dirname(__file__)
    poster_path = os.path.join(dirname, "poster.png")
    if os.path.exists(poster_path):
        return send_file(poster_path, mimetype='image/png')
    return "Not Found", 404

@app.route("/lock")
def lock():
    global CURRENT_KEY, IS_DECOY_SESSION
    CURRENT_KEY = None
    IS_DECOY_SESSION = False
    return "Locked"

@app.route("/list_intruders")
def get_intruders():
    if not CURRENT_KEY or IS_DECOY_SESSION: return jsonify({"images": []}), 401
    if not os.path.exists(NOMEDIA_DIR): return jsonify({"images": []})
    files = [f for f in os.listdir(NOMEDIA_DIR) if f.startswith("security_")]
    return jsonify({"images": files})

@app.route("/intruder/<filename>")
def serve_intruder(filename):
    if not CURRENT_KEY or IS_DECOY_SESSION: return "Unauthorized", 401
    filepath = os.path.join(NOMEDIA_DIR, filename)
    try:
        with open(filepath, "rb") as f:
            blob = f.read()
        plain = decrypt_bytes(INTRUDER_KEY, blob)
        return send_file(io.BytesIO(plain), mimetype='image/jpeg')
    except:
        return "Decryption Failed", 500

@app.route("/delete_intruder/<filename>")
def delete_intruder_route(filename):
    if not CURRENT_KEY or IS_DECOY_SESSION: return "Unauthorized", 401
    filepath = os.path.join(NOMEDIA_DIR, filename)
    if os.path.exists(filepath): os.remove(filepath)
    return jsonify({"status": "deleted"})

INTRUDER_KEY = PBKDF2(b"intruder_master_passphrase", b"pv_salt_v1", dkLen=32, count=200000)

def save_intruder_image(filename, img_bytes):
    blob = encrypt_bytes(INTRUDER_KEY, img_bytes)
    with open(os.path.join(NOMEDIA_DIR, filename), "wb") as f:
        f.write(blob)
    return True

def eval_math(expr):
    try:
        allowed = set("0123456789+-*/. ")
        if not all(c in allowed for c in expr): return "Error"
        return str(eval(expr, {"__builtins__": {}}, {}))
    except:
        return "Error"

def capture_intruder_from_java():
    print("Intruder capture triggered")
    return True

def reset_vault():
    global CURRENT_KEY
    if os.path.exists(NOMEDIA_DIR):
        import shutil
        shutil.rmtree(NOMEDIA_DIR)
        os.makedirs(NOMEDIA_DIR, exist_ok=True)
    
    decoy_dir = os.path.join(STORAGE_DIR, ".nomedia_decoy")
    if os.path.exists(decoy_dir):
        import shutil
        shutil.rmtree(decoy_dir)
        
    if os.path.exists(META_FILE):
        with open(META_FILE, "w") as f:
            json.dump({}, f)
    CURRENT_KEY = None
    return True

def run_flask():
    app.run(port=5000, host="0.0.0.0", threaded=True)

# Integration support for:
# 1. PANIC ACTION (SHAKE-TO-HIDE & VOLUME DOWN 3-CLICK)
# 2. SMS REMOTE WIPE VIA reset_vault()
# 3. FAKE CRASH DIALOG ON 3-TAP

