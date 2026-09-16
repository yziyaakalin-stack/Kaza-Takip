# Kaza Takibi (Android)

Kaza namazı takip uygulaması. İnternet izni istemez, kayıtlar telefonda saklanır.

## APK almak (GitHub Actions, bilgisayara kurulum gerekmez)
1. GitHub'da yeni, private bir repo aç.
2. Bu klasördeki her şeyi (gizli `.github` klasörü dahil) repoya yükle.
3. Repo'da Actions sekmesine gir, "APK oluştur" işinin bitmesini bekle (3-5 dk).
4. Biten işe tıkla, Artifacts kısmından `KazaTakibi-apk` dosyasını indir, zip'i aç.
5. `app-debug.apk` dosyasını telefona at ve kur ("Bilinmeyen uygulamaları yükle" izni gerekir).

## Android Studio ile
Klasörü Android Studio'da aç, Gradle eşitlemesinden sonra Build > Build APK(s).

## Arayüzü güncellemek
Arayüz `app/src/main/assets/index.html` dosyasıdır. Değiştir, tekrar push et, yeni APK oluşur.
Güncelleme kurulumunda kayıtlar korunur; uygulamayı silersen silinir.
