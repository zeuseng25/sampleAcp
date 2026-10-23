# Görevler: Derleme Hatasını Düzeltme

- **Spec no:** 009 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı — *2026-10-23*

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`

Ledger notu: depo git değil; commit adımları yok, kayıt bu dosya.

- Ruling: plan 010'dan önce yazıldı. Araç tanımları artık `AracSetleri`'nde;
  `dogrula` yalnızca `AracSetleri.DUZENLEME`'ye (Çok dosyalı) girer — Sohbet ve
  Uzman salt okunur kalır (spec 009 yalnızca çok dosyalı kipi kapsıyor). — Bedeli: yok.

---

## Saf katman (`dev.acp.komut`, `com.intellij` yok)

- [x] T10 `KomutTahmini` ✅
- [x] T11 `KomutSatiri.ayristir` ✅
- [x] T12 `KomutDenetimi` ✅
- [x] T13 `CiktiKirpici` ✅ — `KomutSafTest` (11) T10-T13 birlikte
- [x] T14 `ArchitectureTest` genişledi ✅

## Ayarlar

- [x] T20 → `DogrulamaAyarlariTest` (5) ✅
  - Ruling: test ve uygulama aynı adımda yazıldı; kırmızı aşaması gözlenmedi (derleme hatası olurdu). — Bedeli: testin geçersiz olma ihtimali; davranışlar basit getter/varsayılan.
- [x] T21 Ayar ekranları ✅ (proje sayfası ACP'nin altında; zaman aşımı saniye olarak gösteriliyor) — gözle doğrulama T90'da
- [x] T22 AGENTS.md Kural 6 ✅

## Çalıştırma

- [x] T30 `KomutCalistirici` → `KomutCalistiriciTest` (8) ✅; iptalde alt süreç ölümü **mutasyonla doğrulandı**; 20 MB çıktıda yakalama 2 MB'ı aşmıyor

## Araç ve onay

- [x] T40 `dogrula` + yürütücü → `DogrulaAraciTest` (6) ✅; "Atla" kapısı **mutasyonla doğrulandı**
  - Ruling: komut kökü yürütücüye isteğe bağlı parametre (`komutKoku`); hafif test projesi bellek içi dosya sisteminde. Üretimde kök yerel diskte değilse komut çalışmaz, söylenir. — Bedeli: yok.
- [x] T41 Kural 6 + **`AjanYonergesiTest` ilk kez yazıldı** ✅ (belgesi anıyordu ama test yoktu)
- [x] T50 Onay kapısı → `AjanDogrulaOnayTest` (3, gerçek istemci + gerçek süreç) ✅; beklerken durdur → komut çalışmıyor, geç gelen tık etkisiz
- [x] T51 Onay şeridi → `KomutOnaySeridiTest` (3) ✅; sonuç adımlarda (`dogrula: başarılı` / `başarısız (çıkış N)` / `atlandı`)

## Bağımsız gözden geçirme (2026-10-23)

Taze bir gözden geçiren (opus): 0 kritik, 6 önemli, 9 küçük. "Modelin komutu
seçemediği" doğrulandı. Yeniden derecelendirme: bulgu 9 ve 10 küçükten önemliye
(kullanıcıya doğrudan dokunuyor). Tek tur; önce tipler davranışsız eklenip 9 testin
**davranış** nedeniyle kırmızı olduğu görüldü, sonra düzeltildi:

- Final: fixed 1 (kökteki aynı adlı dosya PATH programının yerine geçiyordu) — `testKokteAyniAdliDosyaSistemProgramininYerineGecmez` RED→GREEN; yalnızca `./x` ve Windows sarmalayıcıları kökten
- Final: fixed 2 (durdurulan komut "çalışıyor"da kalıyordu) — `testCalisirkenDurdurulursaAdimDurdurulduOlur` RED→GREEN
- Final: fixed 3 (çıktı kullanıcıya gösterilmiyordu) — `testBasarisizCiktiKullaniciyaGosterilir`, `testKomutBilgisiGorunur` RED→GREEN
- Final: fixed 4 (tanımsız komut yalnızca modele söyleniyordu) — `testTanimsizKomutKullaniciyaDaBildirilir`, `testTanimsizKomutAyarYoluyla` RED→GREEN; panel ayar yolunu gösteriyor
- Final: fixed 5 (derleyicinin mutlak yolları dosya araçlarıyla açılamıyordu) — `testMutlakYollarProjeKokuneGoreliOlur` RED→GREEN
- Final: fixed 6 (her başlatma hatası "bulunamadı") — `testCalistirmaIzniYoksaBulunamadiDenmez` RED→GREEN; ilk düzeltme macOS'un `error: 2` biçimini kaçırdı, mevcut test yakaladı
- Final: fixed 9 (Windows'ta Türkçe çıktı bozuk; JDK 18+ `defaultCharset` = UTF-8) — `native.encoding`
  - Ruling: kırmızı test yazılamadı (Windows/cp1254 yok). — Bedeli: düzeltme akıl yürütmeyle doğrulandı.
- Final: fixed 10 (tek dev satırda modele neredeyse hiçbir şey gitmiyordu; sığmayan ilk hatadan sonrakiler kayboluyordu) — iki `KomutSafTest` testi RED→GREEN
- Final: minor (deferred): 7 — `--file=/x`, `-Dx=/y` gibi `=`'li yollar ve sembolik bağlantılar sınır denetiminden geçiyor; mutlak program yolu (`/opt/homebrew/bin/mvn`) reddediliyor
- Final: minor (deferred): 8 — Windows'ta `.cmd/.bat` cmd.exe üzerinden çalışır; `&`, `%`, `^`, `!` engellenmiyor (girdi kullanıcının kendi ayarı)
- Final: minor (deferred): 11 — başarıda torun süreçler öldürülmüyor; stdout'u miras alan torun okuyucu iş parçacığını bekletebilir
- Final: minor (deferred): 12 — onay metni tırnakları göstermiyor (`["a b"]` ile `["a","b"]` aynı görünür)
- Final: minor (deferred): 13 — Windows'ta `tools/build.cmd` IDE dizinine göre çözülür
- Final: minor (deferred): 14 — proje ayar sayfası "Yeni projeler için ayarlar"da da görünüyor (`nonDefaultProject` yok)
- Final: minor (deferred): 15 — 2 MB yakalama, 8 000 karakter model bütçesi sabit (planın bilinçli kararı; Kural 6 tartışmalı)

Sonuç: **577/577**. `verifyPlugin`: üç sürümde Compatible, dahili API yok, 009 kodundan uyarı yok.

## Gerileme ve doğrulama

- [x] T80 → **577/577** ✅ (gözden geçirme sonrası); eskilerden yalnızca `AracSetleriTest` (010'da yazılmış) `dogrula`'yı içerecek şekilde güncellendi
- [x] T90 `runIde` → **kullanıcı doğruladı** ✅ *2026-10-23* (graphify, Maven): hata → onay → düzeltme → pencerede önizleme → uygula
  - *2026-10-23, ilk deneme (graphify, Maven):* **düzeltemedi.** Kök neden: komut kabuğun
    `JAVA_HOME`'unu (JDK 17) devralıyordu; proje 21 istiyor, IDE projeyi kendi SDK'sıyla (JDK 25)
    derliyor. Derleme koda hiç ulaşmadı (`release version 21 not supported`), ajan gerçek hatayı
    görmedi ve bir ortam hatasını kodla düzeltmeye çalıştı. Aynı komut proje SDK'sıyla elle
    koşulunca gerçek hatalar çıktı (`LdapAdministration.java:[49,38] cannot find symbol`).
  - Düzeltme: proje SDK'sı Java ise komuta `JAVA_HOME` olarak verilir (`JavaSdkType` platform
    çekirdeğinde) — `testEkOrtamKomutaGecer` RED→GREEN. Yönerge: hata kodda değilse kodu
    değiştirme, kullanıcıya söyle — `ortam hatasi kodla duzeltilmeye calisilmaz`.
    - Ruling: SDK→JAVA_HOME bağlantısı test ortamında sınanamadı (hafif projede SDK yok); doğrulama T90'ın tekrarı. Yönerge testinin kırmızısı derleme hatası arkasında kaldı, ayrıca gözlenmedi. — Bedeli: bağlantı hatalıysa T90'da aynı belirti görülür.
  - Ders: ajan yolu günlük yazmadığı için teşhis günlükten yapılamadı; proje bulunup komut elle yeniden üretildi.
  - *İkinci deneme:* "uzun bir şeyler yazdı, sonuç üretmedi, bitti mi anlamadım". İki görünüm hatası:
    ajan bölümü kaydırma alanının dışındaydı (uzun komut çıktısı/son mesaj sohbet alanını **-7120 px**'e
    itti) ve açık bir durum satırı yoktu. Düzeltme: ajan bölümü yüksekliği sınırlı kendi kaydırma
    alanında; en üstte durum satırı (Çalışıyor… / Bitti — N dosyada değişiklik hazır / Bitti —
    değişiklik önerilmedi / Durduruldu / hata) — `AltPanelYerlesimTest` (3) RED→GREEN (biri hata
    şeridi için koruma; "aynı NORTH" şüphem yanlış çıktı). İlk görünürlük koşulu 008'in özet
    şeridini gizledi; `AjanSeridiTest` yakaladı, koşul genişletildi.
  - Teşhis için içeriksiz günlük: araç adı, bitiş nedeni, sonuç sayıları, `dogrula` sonuç türü ve
    çıkış kodu (spec 008 "kod günlüğe yazılmaz" korunuyor; argüman ve çıktı yazılmıyor). Toplam **582**.
  - *Üçüncü deneme:* **ajan hatayı düzeltti** — günlük: `dogrula` başarısız (çıkış 1) → arama/okuma
    → `dosya_duzenle` → 1 dosya, 1 değişiklik; `graphify` artık derleniyor (çıkış 0, elle doğrulandı).
    Not: kullanıcının ajan kipi 008 denemesinden `DOGRUDAN` kalmış; önizlemesiz uygulandı.
    Kullanıcı bulgusu: "cevap ekranın yarısında, kaydırma orada". Kullanıcı onayıyla: ajan turu
    **sohbet akışına** iki mesaj olarak çiziliyor (görev + cevap; adımlar cevabın üstünde, komut
    çıktısı kod bloğu). Şeritte yalnızca durum, onay ve uygulama bağlantıları — `AjanTuruSohbetteTest`
    (2) RED→GREEN. Toplam **584**.
    - Ruling: çizim görünümde; sohbet servisine yazılmıyor (008 T73 yalıtımı, modele giden sohbet
      geçmişi değişmiyor). Bedeli: yalnızca **son** ajan turu görünür — yeni tur veya "Vazgeç"
      öncekini sohbetten kaldırır. Kalıcı ajan geçmişi istenirse ayrı iş.
  - *Dördüncü deneme:* akış doğru (günlük: `dogrula` başarısız → arama/okuma/`kullanimlari_bul` →
    `dosya_duzenle` → 1 dosya, `kip=ONIZLE`); sohbet görünümü "çok doğru". Tek bulgu: **diff
    sekmede açıldı, pencere olmalı** (003'teki gibi). Düzeltme: `DiffManager.showDiff(zincir,
    FRAME)`; pencere uygulanınca, vazgeçilince ve yeni turda kapanıyor (003 kalıbı). Kullanımdan
    kalkmış `openFile` çağrısı da gitti.
    - Ruling: pencere testi yazılamadı — başsız test ortamı çerçeve açamıyor (`FrameWrapper.getFrame`
      NPE). Ölçümün kendisi sınandı: kullanıcının doğruladığı 003 penceresi de aynı ortamda aynı
      hatayla düştü → ortam ölçemiyor, kod değil. Doğrulama gözle (T90). — Bedeli: yok.
  - Teşhis için sohbet araştırma yoluna da içeriksiz günlük eklendi (araç adı + süre, bitiş nedeni).
- [x] T91 `spec.md` durumu "Uygulandı" ✅
