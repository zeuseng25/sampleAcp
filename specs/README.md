# Spec-Driven Geliştirme

Bu projede kod, üç aşamalı bir kapıdan geçmeden yazılmaz:

```text
spec.md   NE + NEDEN     → onay →
plan.md   NASIL          → onay →
tasks.md  hangi sırayla  → onay → kod
```

Şablonlar: [000-template/](000-template/)

## Spec listesi

| No | Ad | Durum |
|----|----|-------|
| [001](001-cekirdek-ve-model-baglantisi/) | Çekirdek ve model bağlantısı | Uygulandı — kurumsal TLS **doğrulanamadı** |
| [002](002-sohbet-paneli/) | Sohbet paneli | Uygulandı |
| [003](003-secim-aksiyonlari/) | Seçim aksiyonları | Uygulandı |
| [004](004-paketleme-ve-adlandirma/) | Paketleme ve adlandırma | Uygulandı |
| [005](005-kurum-ici-dagitim/) | Kurum içi dağıtım | Beklemede — altyapı bilgisi |
| [006](006-satir-ici-tamamlama/) | Satır içi kod tamamlama | Uygulandı |
| [007](007-cok-satirli-tamamlama/) | Çok satırlı kod tamamlama | Uygulandı |
| [008](008-cok-dosyali-duzenleme/) | Çok dosyalı düzenleme (agent) | Uygulandı |
| [009](009-derleme-hatasi-duzeltme/) | Derleme hatasını düzeltme | Uygulandı |
| [010](010-uzman-kod-anlama/) | Uzman kod anlama | Uygulandı |

### Planlanan sonraki spec'ler

- **Kısmi öneri kabulü** — 007'de "hepsi ya da hiçbiri" seçildi. Kullanımda
  rahatsız edici çıkarsa satır satır kabul ayrı bir spec olur.

## Her konu kendi spec'ini alır

Bir spec tek bir konuyu anlatır. Birbirinden bağımsız konular aynı dosyaya
yazılırsa spec okunmaz hâle gelir ve kapsam sınırı bulanıklaşır.

Ölçü: **spec'i tek oturumda okuyup ne yapılacağını anlayabiliyor musun?**
Anlayamıyorsan bölünmeli. Pratikte 3–4 hikâyeden sonrası şüphelidir.

Yeni bir istek mevcut bir spec'in kapsamına girmiyorsa, o spec'e eklenmez —
yeni numara alır. Numaralar **oluşturulma** sırasını gösterir, uygulanma sırasını
değil; 004'ün 002'den önce uygulanmış olması normaldir.

## Yeni özelliğe başlama

1. Sıradaki numarayı bul: `ls specs/` → en büyük NNN + 1
2. `specs/NNN-kisa-kebab-ad/` klasörünü aç
3. `000-template/spec.md`'yi kopyala ve doldur
4. **Dur ve onaylat.** `plan.md`'e kendi kendine geçme.

## spec.md — NE ve NEDEN

Yazılır: problem, amaç, kullanıcı hikâyeleri, kabul kriterleri, hata durumları,
kapsam dışı.

**Yazılmaz:** teknoloji adı, kütüphane, dosya yolu, sınıf adı, fonksiyon imzası.

Test: Teknik olmayan biri okuyup neyin yapılacağını anlıyor mu? Hayırsa fazla teknik.

Kabul kriterleri gözlenebilir olmalı:

- ✅ "Kullanıcı iptal düğmesine bastığında akış durur ve o ana kadarki metin ekranda kalır"
- ❌ "İptal düzgün çalışır"

`## Kapsam dışı` bölümü, işi başka bir spec'e devrediyorsa **oraya bağlantı verir.**

Belirsizlik varsa `## Belirsizlikler` altına soru olarak yaz ve **sor**.
Cevaplanmamış soru varken `plan.md`'e geçme.

## plan.md — NASIL

Yazılır: seçilen yaklaşım, elenen alternatifler ve gerekçeleri, Kotlin arayüzleri,
`plugin.xml` uzantıları, değişecek dosyalar, riskler, test stratejisi, uygulama sırası.

İki kural:

- Her başlık `spec.md`'deki bir hikâyeye dayanmalı. Dayanmıyorsa ya spec eksik
  ya kapsam kayması var.
- **Yeniden kullanılacak mevcut kod** bölümünü doldurmadan geçme. Yazmadan önce ara:
  benzer bir şey `src/main/kotlin/dev/acp/` altında zaten var mı? Platformun kendisi
  bunu zaten sunuyor mu?

Riskli parçalar uygulama sırasında **başa** alınır. Bu projede en riskli iki şey:
kurumsal ağda TLS/vekil, ve platform API'lerinin hedef IDE sürümünde var olup olmadığı.

## tasks.md — Sıra

Her görev iki şartı sağlar: tek oturumda bitirilebilir, gözlenebilir sonucu var.

Format: `- [ ] T20 <ne yapılacak> → <nasıl doğrulanır>`

- ✅ `T20 SseParser yazılır → ./gradlew test yeşil, 11 test geçer`
- ❌ `T20 SSE desteği eklenir`

Paralel çalışılabilenler `[P]` ile işaretlenir.

## Uygulama sırasında

- Görev bitince `[x]` yap, satır sonuna kısa not düş.
- **Plandan sapıldıysa** `tasks.md` içindeki `## Notlar` bölümüne **neden** sapıldığını
  yaz. Sessizce sapma — plan yanlışsa plan güncellenir.
- Yeni komut veya konvansiyon çıktıysa [AGENTS.md](../AGENTS.md)'i güncelle.

## Kapanış

`spec.md` kabul kriterlerinin tamamı elle doğrulandıktan sonra durumu "Uygulandı" yap.
Kriterlerden biri karşılanmadıysa özellik **bitmemiştir** — kısmen bitirip "tamam" deme,
neyin eksik kaldığını açıkça söyle.

## Mimari kararlar

Bir özelliğin ötesinde, sistem geneline etki eden kararlar `specs/` değil
`plans/NN-konu-YYYY-AA-GG.md` altına yazılır.
