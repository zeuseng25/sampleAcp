# Görevler: <Özellik Adı>

- **Spec no:** NNN — [spec.md](spec.md) · [plan.md](plan.md)
- **Durum:** Başlamadı | Devam ediyor | Tamamlandı

> **Bu dosyanın kuralı:** `plan.md` onaylanmadan yazılmaz. Her görev
> **tek oturumda bitirilebilir** ve **gözlenebilir bir sonuç** üretir.
> "Sohbet panelini yaz" görev değildir; "panel açılır ve sabit bir metin gösterir"
> görevdir. Tamamlananları `[x]` yapın, notunu satır sonuna ekleyin.

Görev formatı: `- [ ] T<no> <ne yapılacak> → <nasıl doğrulanır>`
Paralel çalışılabilecek görevler `[P]` ile işaretlenir.

---

## Hazırlık

- [ ] T01 ... → ...

## Saf çekirdek (IDE gerekmez)

- [ ] T10 ... → `./gradlew test` yeşil

## Platform entegrasyonu

- [ ] T20 ... → `./gradlew runIde` ile gözle doğrulanır

## UI

- [ ] T30 ... → ...

## Testler

- [ ] T40 ... → `./gradlew check` yeşil

## Doğrulama ve kapanış

- [ ] T90 `spec.md` kabul kriterlerinin tamamı `runIde` ile elle doğrulanır
- [ ] T91 `spec.md` durumu "Uygulandı" yapılır
- [ ] T92 `AGENTS.md` gerekiyorsa güncellenir (yeni komut, yeni konvansiyon)

---

## Notlar

Uygulama sırasında ortaya çıkan sapmalar, kararlar ve nedenleri.
Plandan sapıldıysa **neden** sapıldığı buraya yazılır.
