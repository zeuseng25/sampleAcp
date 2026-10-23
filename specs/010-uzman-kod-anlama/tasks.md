# Görevler: Uzman Kod Anlama

- **Spec no:** 010 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı — *2026-10-23* (T53 dahil)

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`

---

## Deneme

- [x] T00 Üst tanım yolu + gerçek sunucuda araç çağrısı/metin sırası ✅ *2026-10-22* — kod yazılmadan kapandı
  - Ruling: **üst tanım aracı yok.** Platformdaki "Go to Super" yalnızca
    `LanguageCodeInsightActionHandler.invoke(project, editor, file)` — editörü
    gezdiren, sonuç döndürmeyen bir eylem. Spec'teki yedek yol uygulanır:
    yönerge `uygulamalari_bul` + `kullanimlari_bul`'u birleştirtir ve sınırı
    cevapta söyletir. — Yanlışsa bedeli: polimorfik çağrılar eksik listelenir, cevap bunu söyler.
  - Ruling: **sıra ölçülmedi; tasarım sıradan bağımsız yapıldı.** Metin parçaları
    hemen akar; aynı turda araç çağrısı gelirse döngü `MetinGeriAl` olayı yayar
    ve servis o turun metnini cevaptan `reasoning`'e taşır. Tamponlama yok →
    sunucunun sırasına bağımlılık yok. — Bedeli: araçlı turda metin kısa süre
    görünüp kaybolabilir.
  - Ledger notu: depo git değil; commit adımları yok, kayıt bu dosya.

## Döngü (saf)

- [x] T10 `AjanDongusu(akisli=true)` → `AkisliDonguTest` (7) ✅
  - Ruling: araçlı turun metni `Dusunuyor` değil `MetinGeriAl` ile geri alınır (T00 kararı); ayrıca `calistir(gorev, gecmis)` ve `istekHazirla` eklendi — sohbetin geçmişi ve istek ayarları (sıcaklık, düşünme) döngüye geçebilsin diye. — Bedeli: yok, varsayılanlar 008'i değiştirmiyor.
- [x] T11 `akisli=false` → 008 olayları birebir; `AjanDongusuTest` (15) değişmeden geçti ✅

## Araç setleri ve yürütücü

- [x] T20 `AracSetleri` (saf, `dev.acp.llm.agent`) → `AracSetleriTest` (3) ✅; `AjanService.ARACLAR` kaldırıldı
- [x] T21 `duzenlemeAcik=false` → `dosya_duzenle` reddedilir ✅ **mutasyonla doğrulandı** (koruma kaldırılınca kırmızı)
- [x] T22 İlişki araçları yürütücüde → `SaltOkunurYurutucuTest` (6) ✅; yok/dizinleme → `dosya_ara`'ya yönlendirme

## İlişki araçları (IDE)

- [x] T30 `IliskiBicimi` → `IliskiBicimiTest` (6) ✅ (yakın ad önerisi `Yok.sebep` içinde, T31'de üretilir)
- [x] T31 `PsiIliskiArayici` ✅ — yalnızca `platform.lang` API'leri
  - Ruling: kayıt ayrı `acp-anlama.xml` yerine mevcut `acp-completion.xml`'de — aynı modüle (`lang`) ikinci bir `<depends>` yazılmıyor; dosya adı AGENTS.md ve 006'da geçtiği için yeniden adlandırılmadı. — Bedeli: dosya adı içeriğinin yarısını anlatıyor.
  - Yol kuralları `ProjeYollari`'na çıkarıldı (yürütücü ve ilişki araması aynı kuralı kullansın); 008 yol testleri değişmeden geçti.
- [x] T32 Gerçek IDE denetimi → **10/10 geçti** ✅ *2026-10-22*; bozuk beklentiyle `kaldi=1` (denetim hata yakalayabiliyor)
  - Ruling: iç eylem yerine **bayraklı başlangıç denetimi** (`-Pacp.iliskiDenetimi=true`): iç eylem bir insanın tıklamasını ister; bu, ajan dahil herkesin tekrar koşabileceği, kendini kapatan bir kontrol. Senin `untitled1`'ine dokunmaz, `src/test/testData/iliski`'yi açar. — Bedeli: yok; bayraksız hiçbir şey yapmaz.
- [x] T33 AGENTS.md tuzak #5 eklendi ✅

## Sohbet

- [x] T40 Ayarlar + ayar ekranı ("Kodu araştırma" grubu) ✅
- [x] T41 Araştırma kapalı → gövdede `tools` ve sistem mesajı yok, tek istek ✅
- [x] T42 Araştırma açık → `SohbetArastirmaTest` ✅; Uzman, Sohbet ayarı kapalıyken de araştırıyor
  - Ruling: akışlı kipte döngü `coalesceText` kullanıyor (token başına olay paneli kilitler, spec 001 H1); `AkisliDonguTest`'teki parça beklentisi "birleşik metin" olarak gevşetildi. — Bedeli: yok.
  - Sahte sunucuya `ARAC_CAGIR`, `ARAC_HEP_CAGIR`, `ARACLARI_REDDET` kipleri ve `chatRequestBodies` eklendi (mevcut kipler değişmedi).
- [x] T43 Yönergeler → `ArastirmaYonergeleriTest` (5) ✅
- [x] T44 Araç reddi (vLLM'in gerçek 400 metniyle) → araçsız tekrar, bir kez bildirim ✅
  - Ruling: "oturum" = sohbet temizlenene kadar; `clear()` bayrağı sıfırlar (sunucu ayarı değişmiş olabilir). Testler arasında sızan bayrak bu kararı zorunlu kıldı. — Bedeli: temizledikten sonra ilk soru bir kez daha 400 alıp araçsıza düşer.
- [x] T45 Durdurma → "— durduruldu"; adım sınırı/tekrar → açık not ✅ (yalnızca araştırma yolunda; düz sohbetin 002 davranışı değişmedi)

## Arayüz ve eylem

- [x] T50 Kip seçici `Sohbet | Uzman | Çok dosyalı` → 280 px'te üçü de sığıyor; her kipin kendi ipucu ✅
  - Ruling: 008'in `KipSeciciTest`'i **değiştirildi** (iki seçenek → üç). Spec 010 seçicinin kendisini değiştiriyor; eski beklenti artık yanlış davranışı tarif ederdi. Yerleşim ve ipucu kontrolleri aynen korundu. — Bedeli: T80'in "değiştirilmeden" ifadesine bu tek istisna.
- [x] T51 Adım satırı → `MesajAdimlariTest` (2) ✅
  - Ruling: ayrıntı "tıklanınca açılır" yerine **ipucunda** (tooltip): daha az kod, dar panelde yerleşim riski yok. — Bedeli: dokunmatik/klavye kullanıcısı ayrıntıyı göremez; gerekirse sonra açılır panel.
- [x] T53 Son kip proje bazında hatırlanır → `KipHatirlamaTest` (4) RED→GREEN ✅ *2026-10-23*; toplam **588**
  - Ruling: bağlantı katmanındaki okuma/yazma (`ChatToolWindowFactory`) birim testli değil. **Kullanıcı runIde'de doğruladı** ✅ (graphify `workspace.xml`: `sonKip=COK_DOSYALI`, yeniden açılışta korundu).
- [x] T52 "Seçili kodu açıkla" → Uzman, `Konum: paket/Ornek.kt, satır 2–3` → `AciklaUzmanTest` ✅
  - Ruling: `acikla.md` Uzman'ın bölüm yapısıyla çelişmeyecek biçimde sadeleşti ("yalnızca açıkla" korundu → 003 testleri değişmeden geçiyor). — Bedeli: yok.

## Bağımsız gözden geçirme (2026-10-22)

Taze bir gözden geçiren (opus): 0 kritik, 8 önemli, 6 küçük. Tek düzeltme turu, her biri önce kırmızı:

- Final: fixed bulgu 1 (araçlar diskteki, satırlar editördeki hâlden) — `GuncelMetinTest` (3) RED→GREEN
- Final: fixed bulgu 2 (belge PSI'ya işlenmeden okuma) — `constrainedReadAction(inSmartMode, withDocumentsCommitted)`
  - Ruling: kırmızı test yazılamadı (dil eklentisi test ortamında yok; denetimde belge değiştirmek örnek projeyi kirletir). Gerçek IDE denetimi gerileme olarak 12/12. — Bedeli: düzeltme yalnızca akıl yürütmeyle doğrulandı.
- Final: fixed bulgu 4 (kapsayan = yerel değişken) — denetime `java/kotlin-atamada-kapsayan` eklendi, gerçek IDE'de RED (`oku.x`) → GREEN (`Tuketici.oku`). İlk düzeltme JS'de dosyaya özel fonksiyonu da atlattı; denetim yakaladı, ölçüt "kapsamı dosyadan dar" olarak daraltıldı. 12/12.
  - Denetim kendisi de düzeltildi: kapalıyken eklenen örnek dosyalar için VFS yenileme + `requestReindex`.
- Final: fixed bulgu 5 (her "tool" 400'ü araştırmayı kapatıyordu) — `testAracDesteginiGosterenBaskaBir400ArastirmayiKapatmaz` RED→GREEN; araç zaten çalıştıysa hata gösterilir
- Final: fixed bulgu 6 (adım sınırında cevap yok) — `adim sinirinda aracsiz son tur kismi cevap verir` RED→GREEN; yalnızca akışlı kip
- Final: fixed bulgu 7 (önceki soruların bağlamı tekrar gidiyordu) — `testOncekiSorularinBaglamiTekrarGitmez` RED→GREEN
- Final: fixed bulgu 8 (yeni istek, eskisinin kapanışıyla bozuluyordu; 002'den beri) — `testYeniIstekEskisininKapanisiylaBozulmaz` RED→GREEN; iş önce atanıp sonra başlatılıyor (yarış önlemi)
- Final: Ruling: bulgu 3 (uzun kullanım araması tek okuma eyleminde, yazmada baştan başlar) **düzeltilmedi** — gözden geçiren de ölçmedi; mimari değişiklik ölçülmemiş varsayıma dayanmamalı. T90'da büyük projede ölçülür. — Bedeli: kullanıcı yazarken arama uzayabilir; durdur düğmesi çalışır.
- Final: minor (deferred): `LENGTH` bitişi sessiz — uzun Uzman cevabı kesik kalabilir (T90'da `maxTokens` ile kontrol)
- Final: minor (deferred): araçsız geri düşüşte "Açıkla" şablonu araştırma istiyor, sistem yönergesi yok
- Final: minor (deferred): 400+ sonuçta toplam yanlış ("400+" yazılmalı)
- Final: minor (deferred): "— durduruldu" bundle'da değil; not metinleri sonraki istem geçmişine giriyor
- Final: minor (deferred): tam satır seçimde son satır bir fazla
- Final: minor (deferred): `IliskiDenetimi` üründe kayıtlı (bayraksız etkisiz); `IliskiArayici` yorumunda eski `acp-anlama.xml` adı; `AjanYonergesi` yeni araçları anmıyor
- Final: minor (deferred, 008'den): `BaglamKirpici.VARSAYILAN_BUTCE` `AcpSettings.State` dışında (Altın Kural 6)

Sonuç: **525/525** test; gerçek IDE denetimi **12/12**.

## Gerileme ve doğrulama

- [x] T80 Gerileme kapısı → **518/518** ✅; eski 478'den yalnızca `KipSeciciTest` değişti (T50 ruling). `verifyPlugin`: üç sürümde Compatible, dahili API 0; 010 kodundan uyarı yok
- [x] T90 `runIde`'de kabul kriterleri elle + ölçüm ✅ *2026-10-23* — kullanıcı: Uzman cevapları tam, konumlar doğru, uydurma yok
  - *2026-10-22, ilk deneme:* **uzun yatay kaydırma** (kullanıcı bulgusu). İki sebep: adım satırı
    (tek satırlık etiket tam metin genişliğini istiyordu) ve uzun yollu cevap metni (010 öncesinden
    beri; Uzman'ın `dosya:satır` yolları görünür kıldı). Kök: sohbet paneli viewport genişliğini
    izlemiyordu → `Scrollable` + `tracksViewportWidth`. `YatayKaydirmaTest` (3) RED→GREEN;
    ölçüm önce bilinen-iyi kısa cevapla sınandı. Toplam **528**.
    runIde'de kullanıcı doğruladı ✅ *2026-10-23*. Kalan: 5 sorunun konum/uydurma ölçümü.
- [x] T91 `spec.md` durumu "Uygulandı" ✅
