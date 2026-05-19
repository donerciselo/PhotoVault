# 🔒 PhotoVault - Gizli Hesap Makinesi Kasası & Medya Deposu

PhotoVault, dışarıdan bakıldığında tamamen işlevsel bir **hesap makinesi** gibi görünen, ancak arka planda askeri düzeyde şifrelemeyle donatılmış son derece gelişmiş bir **gizli galeri ve güvenlik merkezidir**. Android (Java) ve Python (Chaquopy) entegrasyonuyla geliştirilen bu proje, gelişmiş sızma koruma sistemleri, uzaktan imha ve bulut yedekleme yeteneklerine sahiptir.

---

## 🚀 Öne Çıkan Özellikler

### 1. Kamuflaj & Arayüz Yetenekleri
* **Gerçek Hesap Makinesi Maskesi:** Uygulama açıldığında dört işlem yapabilen, girdileri tek tuşla silebilen (`C` butonu) ve ekran kaydırma hareketiyle geri alan standart bir hesap makinesidir.
* **Gizli PIN Girişi:** 4 haneli PIN kodunuzu girip `=` tuşuna bastığınızda şifreli kasanız açılır.
* **Tuzak Kasa (Decoy Vault):** Zorla şifrenizin istendiği durumlarda girmek üzere sahte bir PIN belirleyebilirsiniz. Bu PIN girildiğinde, tamamen zararsız görseller içeren sahte bir galeri açılır.
* **🧬 Biyometrik Doğrulama (Hızlı Giriş):** Güvenli hesap makinesi ekranı açıldığında, parmak iziniz veya yüz tanımanız ile doğrudan şifreli kasanıza hızlı erişim sağlayabilirsiniz.

### 2. İleri Düzey Güvenlik & Savunma Sistemleri
* **🚨 Sallayarak Acil Kilitleme (Shake-to-Lock):** Kasa açıkken cihazı hızlıca salladığınızda kasanız anında kilitlenir ve hesap makinesine yönlendirilirsiniz.
* **🎛️ Ses Kısma Tuşu Koruması (Volume Down Panic):** Ses kısma tuşuna hızlıca 3 kez basmak kasayı anında kilitler.
* **⚠️ Sahte Çökme (Crash / ANR) Ekranı:** Hatalı PIN giriş denemelerinde (3. kez yanlış girildiğinde) uygulama sahte bir "Uygulama Yanıt Vermiyor" uyarısı göstererek davetsiz misafirlerin ilgisini dağıtır. (Sol üst köşeye çoklu tıklama ile bu maske aşılabilir).
* **📸 Davetsiz Misafir Fotoğrafı (Intruder Capture):** Üst üste hatalı şifre girildiğinde ön kamera üzerinden arka planda sessizce davetsiz misafirin fotoğrafı çekilir ve kasanın içine kaydedilir.
* **📲 Uzaktan SMS ile Veri İmhası (Remote SMS Wipe):** Telefonunuza önceden belirlediğiniz özel bir kelimeyi içeren bir SMS geldiğinde, uygulama kasanızdaki tüm şifreli medyayı anında ve kalıcı olarak temizler.

### 3. Medya Yönetimi & Şifreleme
* **🔒 AES-256 Askeri Şifreleme:** Kasaya aktarılan her görsel ve video, kasanın içinde AES-256 algoritması kullanılarak şifrelenir ve harici dosya yöneticilerinden gizlenir (`.nomedia`).
* **🗑️ Orijinal Dosyayı Galeriden Otomatik Silme:** Galeriden bir medya içe aktarıldığında, genel depolama alanındaki unencrypted orijinal sürümü sistem izinleriyle otomatik olarak silinir.
* **📹 Görünmez Video Kaydı (Volume Up 2sn):** Ses açma tuşuna 2 saniye basılı tuttuğunuzda, ekran kapalıyken veya herhangi bir bildirim/ses çıkmadan arka planda sessiz video kaydı başlar. Kaydedilen video otomatik olarak kasanın içine şifrelenir.
* **🎬 Kasa İçi Video Oynatıcı:** Şifrelenmiş `.mp4` formatındaki videolar, kasa içerisinden çıkmadan şık bir entegre oynatıcı yardımıyla izlenebilir.

### 4. Sistem & Yedekleme Yetenekleri
* **☁️ Google Drive Bulut Yedeklemesi:** Kasanızı Google hesabınıza bağlayarak şifreli yedekleme yapabilirsiniz. Android `JobScheduler` sayesinde, yedekleme yalnızca **Wi-Fi bağlıyken ve gece 03:00 - 05:00 saatleri arasında** pilden ve veri paketinden tasarruf edecek şekilde arka planda yürütülür.
* **🔄 Kendi Kendini Güncelleme (OTA):** Uygulama açılışta GitHub Releases API üzerinden en son yayınlanan sürümü sorgular ve yeni bir sürüm algılarsa APK'yı otomatik indirerek FileProvider üzerinden güvenle kurar.

---

## 🛠️ Teknolojik Altyapı
* **Android Native (Java):** Arayüzler, Kamera Kontrolü (Camera2/Hardware), Sensör Dinleyicileri, Biyometrik Kütüphaneler, Medya Kayıt Elemanları (`MediaRecorder`) ve Android Servisleri.
* **Chaquopy (Python):** Arka planda çalışan Flask sunucusu, şifreleme motoru (AES-256), dosya yönetimi ve veritabanı indeksleme.
* **Şifreli Depolama:** `.nomedia` dizini altında güvenli, harici dosya tarayıcılarının erişemeyeceği şifreli bloklar.

---

## 💻 Kurulum & Derleme Adımları

### Gereksinimler
* Android Studio (En güncel sürüm önerilir)
* JDK 17 veya JDK 21 (Çalıştırma ortamı için)
* Android cihaz (Biyometrik testler için fiziksel cihaz önerilir)

### Derleme
Proje dizininde terminali açıp aşağıdaki Gradle komutuyla hata almadan debug sürümünü derleyebilirsiniz:

```powershell
# Windows PowerShell için
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
./gradlew assembleDebug
```

Derleme sonrasında üretilen APK dosyasına aşağıdaki dizinden erişebilirsiniz:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚖️ Lisans ve Kullanım
Bu proje eğitim ve kişisel veri gizliliği amacıyla geliştirilmiştir. İzinsiz dinleme, gizli izleme veya kötü niyetli kullanım durumlarındaki tüm sorumluluk kullanıcıya aittir.
