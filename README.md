# Kaza Takibi (Android)

Kaza namazı ve oruç takip uygulaması. İnternet izni istemez, kayıtlar telefonda saklanır.

## Özellikler (1.2)
- İlk açılışta kurulum: cinsiyet, başlangıç ve bitiş tarihi, hayız/lohusalık (yalnızca kadınlarda), vitir, hedef, şehir, önceden kılınanlar, oruç borcu
- Vakit vakit kaza sayaçları, "1 günlük kaza kıldım" butonu, oruç kazası sayacı
- Kerahat uyarısı: seçilen şehrin namaz vakitleri telefonda hesaplanır (Diyanet yöntemi)
- Zikirmatik: namaz tesbihatı (33×3), hazır zikirler, kendi zikrin, hedef ve tur, titreşim, ses tuşlarıyla sayma
- Toplu kaza oturumu: her namaz için tek dokunuş, biten namaz otomatik düşer, ekran açık kalır
- İstatistik: seri, haftalık/aylık toplamlar, son 14 gün, son 12 ay, aşamalar
- Günlük hatırlatma: hedef tamamlandıysa bildirim gelmez
- Yedekleme: dosyaya kaydet, paylaş, dosyadan ya da metinden geri yükle
- Rehber: kılınış, niyet, kerahat vakitleri, sıra, kolaylıklar, oruç kazası

## Yeni sürüm almak
Her push'ta GitHub Actions APK'yı derler ve sürüm olarak yayınlar. En son sürüm:
https://github.com/yziyaakalin-stack/Kaza-Takip/releases/latest/download/KazaTakibi.apk

1.1 ve sonrası sabit anahtarla (`app/kaza-imza.p12`) imzalanır; güncellemeler
eskisinin üzerine kurulur ve kayıtlar korunur. 1.0 farklı bir anahtarla imzalandığı için
1.0'dan 1.1'e geçerken eski uygulamayı bir kez silmek gerekir.

## Arayüzü güncellemek
Arayüz `app/src/main/assets/index.html` dosyasıdır. Android'e özel işlevler
(hatırlatma, dosya kaydetme/açma, ekranı açık tutma) `MainActivity.java` içindeki
`KazaNative` köprüsünden gelir.
