# Görevler: Çok Satırlı Kod Tamamlama

- **Spec no:** 007 — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Tamamlandı — *2026-09-04*

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Ölçüm — her şeyden önce

- [x] T10 **Gecikme ölçüldü — risk yok** ✅ *2026-09-04*
  - `qwen/qwen3.8-27b`, OpenRouter (sağlayıcı Parasail'e sabitlenerek), 4 tur.
  - **`max_tokens` bir maliyet değil, tavan.** 64 ve 256 isteklerinin ikisi de
    **24 token** üretti, ikisi de `finish_reason: stop`, çıktılar birebir aynı.
  - Süre: 64 → ort. 1.44s, 256 → ort. **0.96s**. 256'nın daha hızlı çıkması,
    farkın ağ gürültüsü olduğunun kanıtı. Kararlı durum ikisinde de ~0.8s.
  - **Karar: üst sınır 256'da kalır.** H4 riski tasarım seviyesinde yok.
  - ⚠️ Ölçüm kullanıcının kendi sunucusunda değil OpenRouter'da yapıldı
    (kullanıcının kararı). Mutlak saniyeler oraya taşınmaz; taşınan bulgu
    "model erken duruyor, tavan maliyet doğurmuyor" — bu, modelin ve
    otoregresif üretimin özelliği, altyapının değil.

## Saf çekirdek

- [x] T20 `TamamlamaKipi` + `TamamlamaKipiSecici` → 13 test
- [x] T21 Sağlayıcıda **tek** karar noktası → `TamamlamaKipiSecici.sec()`
  - **Plandan sapma:** `uyeErisimiSonrasiMi` taşınmadı, yerinde bırakılıp kip
    seçiciden çağrıldı. Amaç (tek karar noktası) sağlandı, 006'nın testlerine
    dokunulmadı — gerileme kapısı temiz kaldı.
- [x] T22 `TamamlamaBaglami.Baglam.tabanGirinti` → varsayılan 0, eski çağrılar bozulmadı
- [x] T30 `GirintiKesici` — ilk satır girinti karşılaştırmasına girmez **(mutasyonla doğrulandı: 7 test kırmızıya döndü)**
- [x] T31 `GirintiKesici` — tabandan az girintili ilk satırda durur, o satır atılır
- [x] T32 `GirintiKesici` — boş satır bloğu bitirmez, öneride korunur
- [x] T33 `GirintiKesici` — `maxSatir` her koşulda uygulanır
- [x] T34 `GirintiKesici` — kötü girdi: girintisiz kod, tek satır, boş metin
- [x] T35 `OneriTemizleyici` kip parametresi alır → varsayılan `TEK_SATIR`,
      yani çağıranlar kipi vermedikçe 006 davranışı birebir korunuyor

## Gerileme kapısı

- [x] T40 006'nın **314 testi değiştirilmeden** geçti ✅
  - Toplam 347 = 314 (006) + 33 (yeni). Hiçbir eski test değişmedi veya silinmedi.
  - Bunu mümkün kılan iki karar: yeni parametreler **varsayılan değerli**
    (`kip = TEK_SATIR`, `tabanGirinti = 0`) ve `uyeErisimiSonrasiMi` taşınmadı.

## Ayarlar

- [x] T50 `completionMaxLines` (6) + `completionBlockMaxTokens` (256) eklendi
- [x] T51 Tek satır token sınırı değişmedi → `testSatirOrtasindaTekSatirTavaniGonderilir`
- [x] T52 Ayar ekranına iki alan eklendi

## Sağlayıcı — ilk kez çok satırlı öneri

- [x] T60 Sağlayıcı kipi seçer, kipe göre tavan gönderir **(mutasyonla doğrulandı: 4 test kırmızıya döndü)**
- [x] T61 Çok satırlı yanıt uçtan uca kesiliyor — kapanış `}` ve sonraki fonksiyon atılıyor
- [x] T62 `runIde` — blok içinde çok satırlı öneri beliriyor ✅ *2026-09-04*
- [x] T63 `runIde` — öneri tek tuşla kabul, tek geri alma adımı ✅ *2026-09-04*
- [x] T64 `runIde` — öneri altındaki kodu gizlemiyor ✅ *2026-09-04*
- [x] T65 `runIde` — satır ortasında hâlâ tek satır ✅ *2026-09-04*
- [x] T66 `runIde` — `nesne.` sonrasında öneri yok, IDE'nin kendi listesi açılıyor ✅ *2026-09-04*

## Kullanıcı bulgusundan doğan işler

- [x] T80 Gerçek model çıktılarıyla temizleme testi → `GercekModelCiktisiTest`
- [x] T81 Kesme eşiği modelin kendi hizasından türetilir → girintisiz yazılan blok bütün kalıyor
- [x] T82 Parantez derinliği sayacı → eşik gevşerken kapanış `}` kaçmıyor
- [x] T83 `completionMaxLines` 6 → **12**; 6 sıradan bir gövdeyi kesiyordu
- [x] T84 Teşhis kaydı → öneri gelmediğinde sebebi günlükten görülebiliyor
      (yalnızca ölçüler; kullanıcının kodu veya modelin metni yazılmaz)

## Sağlamlaştırma

- [x] T70 **Yazma akıcılığı** ✅ *2026-09-04* — 256 token tavanı ritmi bozmuyor.
  - Spec'in en kırılgan kriteriydi; T10 ölçümünün tahmini tuttu.
- [x] T71 Çok satırlı öneri gözle görülür ölçüde geç gelmiyor ✅ *2026-09-04*
  - Kanıt: kullanıcı **zaman aşımına dokunmadan** çalıştı; varsayılan 3 sn yetti.
- [x] T72 Sunucu kapalı / hata / zaman aşımı → sessizce öneri yok; 363 test yeşil
- [x] T73 `./gradlew verifyPlugin` → üç IDE sürümünde **Compatible**, dahili API **0** ✅ *2026-09-04*

## Doğrulama ve kapanış

- [x] T90 `spec.md` kabul kriterleri `runIde` ile elle doğrulandı ✅ *2026-09-04*
- [x] T91 `spec.md` durumu "Uygulandı" yapıldı
- [x] T92 `AGENTS.md` güncellendi — kip tablosu, girinti kesici kuralları, teşhis bölümü, iki yeni kural

---

## Notlar

Uygulama sırasında ortaya çıkan sapmalar, kararlar ve nedenleri.
Plandan sapıldıysa **neden** sapıldığı buraya yazılır.

### Zaman aşımı varsayılanı doğrulandı

`enBuyukSayiyiBul` vakasında iki şüpheli vardı: satır sınırı ve zaman aşımı.
Kullanıcı **ayarlara dokunmadan** çalıştığını bildirdi → sebep satır sınırıydı.
`completionTimeoutMs = 3_000` olduğu gibi kalıyor; blok kipinde 80 token
üretmek için yeterli.

### T83 — emniyet supabı, şekillendiriciye dönüşmüştü

Kullanıcı `enBuyukSayiyiBul` için "bir şey üretmedi" dedi. Aynı fonksiyon gerçek
modele soruldu: **80 token, 8 satır, dolu ve doğru bir gövde.** Sorun modelde
değildi.

`completionMaxLines` varsayılanı 6'ydı ve gövdeyi `return`'den önce kesiyordu.
Sınır 12'ye çıkarıldı. Kural olarak: **emniyet supabı sıradan bir girdiyi
kesiyorsa artık emniyet supabı değildir**, öneriyi şekillendiren gizli bir
kısıttır.

### T84 — sessiz hata, teşhissiz hata olmamalı

Öneri gelmediğinde sebebini görmenin **hiçbir yolu yoktu**: model mi boş döndü,
temizleyici mi kesti, zaman aşımı mı oldu? Kullanıcının "bir şey üretmedi"
bildirimini ancak modele elle sorarak teşhis edebildik.

Sessiz hata kararı doğruydu (yazarken hata penceresi açmak kabul edilemez), ama
eksikti. Artık debug seviyesinde ölçüler yazılıyor: kip, token, bitiş nedeni,
ham satır sayısı, sonuç satır sayısı, uygulanan sınır.

`Help → Diagnostic Tools → Debug Log Settings → dev.acp` ile açılıyor.
Kullanıcının kodu ve modelin ürettiği metin **yazılmıyor** — yalnızca sayılar.

Bu, 006'da kaydettiğimiz kuralın kardeşi: *her "kolaylık, gereklilik değil"
yakalaması ne sakladığını günlüğe yazmalı.* Yenisi: **sessizce vazgeçen her yol,
neden vazgeçtiğini ölçülebilir biçimde bırakmalı.**

### T80-T82 — kullanıcı bulgusu, ve ondan çıkan başka bir hata

Kullanıcı `s.length` önerisinde parantezlerin eksik olduğunu bildirdi.

**Teşhis:** temizleyici parantez yemiyor. Gerçek model çıktıları boru hattından
geçirildi (`GercekModelCiktisiTest`), `return s.length();` bozulmadan çıkıyor —
imleçten sonra `}` varken bile. Model de doğru üretiyor: `metin.length()`
parantezli, `sayilar.length` (dizi) parantezsiz.

⚠️ Ama bu ölçüm **OpenRouter'da** yapıldı, kullanıcı kendi sunucusunda test
ediyor. Bu oturumda aynı modelin farklı sunum yığınında farklı davrandığını
zaten görmüştük. Bildirilen hata **doğrulanamadı**, sebebi açık kalıyor.

**Buna karşılık gerçek bir hata bulundu.** Model, çıktının ilk satırını bazen
girintili bazen girintisiz yazıyor — aynı sunucu, aynı ayar, farklı fonksiyon:

```
uzunluk   → "\n    return s.length();"          girintili
tersCevir → "\nString ters = \"\";\nfor (…) {"   girintisiz
```

Girintisiz olduğunda kesici ikinci satırda duruyordu: beş satırlık doğru bir
gövde, `String ters = "";` tek satırına iniyordu. Yanlış değil ama işe yaramaz.

**Çözüm (kullanıcı onayıyla, plandan sapma):** kesme eşiği artık modelin kendi
hizasından türüyor — ilk gövde satırının girintisi ile bizim girintimizden
küçük olanı. Eşik gevşediği için kapanış `}`'ını ayırt etmek gerekti; onu
parantez derinliği sayacı yapıyor (ayrıştırıcı değil, sayaç — yanılırsa bedeli
önerinin kısa/uzun olması, kodun bozulması değil).

**Bir testi değiştirmek gerekti** ve bu bir gerileme değil: eski test
(`girintisiz kodda ilk satirdan sonrasi kesilir`) tam olarak yukarıdaki hatalı
davranışı kodluyordu. Uydurma girdiyle yazılmıştı; gerçek model çıktısı
karşısında yanlış olduğu görüldü.

**Ders:** saf testleri uydurma girdiyle yazmak, boru hattının doğru
çalıştığını gösteriyor ama **gerçek girdiyle** çalıştığını göstermiyor. Bu spec'te
iki hata da (baştaki girinti, girintisiz blok) ancak gerçek çıktı kullanılınca
görüldü.

### T30 — testim doğruydu, kodum yanlıştı

Baştaki yeni satırı atmak için `removePrefix("\n")` yazdım. İki test kırmızıya
döndü ve haklıydılar: **yalnızca `\n`'i atmak yetmiyor.**

İmleç zaten 4 girintide. Model `\n    int c = a + b;` üretiyor. Sadece `\n`
atılırsa geriye `    int c = a + b;` kalır ve imlecin girintisine eklenince
**8 boşlukla** girer:

```java
void hesapla(int a, int b) {
            int c = a + b;      ← 8 boşluk, yanlış
```

Doğrusu: `\n` ile birlikte, taban girintiyi **aşmayan kadar** boşluk da atılır.
Model daha derin girintilemişse fazlası korunur.

Bu hatayı yakalayan şey, testi gerçek model çıktısından yazmış olmaktı —
uydurma bir girdi kullansaydım muhtemelen fark etmezdim.

### T10 — sağlayıcı, modelden daha belirleyici çıktı

İlk ölçüm savruktu: aynı model, aynı istek, süre 1.04s ile 4.78s arasında
gidip geldi ve **çıktı kalitesi bile değişti**. Sebep OpenRouter'ın her isteği
farklı sağlayıcıya yönlendirmesi.

- **Parasail**: düzgün FIM. Yalnızca ortadaki kod, doğru girinti, imza tekrarı yok.
- **Reka**: FIM yok. İmzayı baştan yazıyor, kapanış `}`'ını tekrarlıyor,
  markdown çitiyle sarıyor, dili C++ sanıyor.

Yani "bu model FIM desteklemiyor" demek yanlış olurdu — **sunum yığını**
destekliyor ya da desteklemiyor. Kullanıcının SGLang kurulumunun neden
çalıştığını da bu açıklıyor: ham prompt'u modele olduğu gibi veriyor.

Ölçüm ancak sağlayıcı sabitlendikten sonra anlam kazandı
(`provider: {only: ["Parasail"], allow_fallbacks: false}`).

### Ölçümden çıkan yeni temizleme kuralı

Parasail'in çıktısı **baştaki yeni satırla** geliyor:

```
'\n    int c = a + b;\n    System.out.println("Toplam: " + c);\n'
```

Prompt'un öneki `{\n    ` ile bitiyordu, yani imleç zaten 4 girintide. Öneri
olduğu gibi kabul edilirse imlecin bulunduğu satır **boş kalır**:

```java
void hesapla(int a, int b) {
                                ← istenmeyen boş satır
    int c = a + b;
```

Çok satır kipine bir kural daha ekleniyor: **imlecin satırında yalnızca boşluk
varsa, önerinin başındaki yeni satır atılır.** Tek satır kipinde bu sorun
görünmüyordu, çünkü zaten ilk satırdan sonrası kesiliyordu.
