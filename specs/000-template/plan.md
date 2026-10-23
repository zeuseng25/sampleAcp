# Plan: <Özellik Adı>

- **Spec no:** NNN — [spec.md](spec.md)
- **Tarih:** YYYY-AA-GG
- **Durum:** Taslak | İnceleme | Onaylandı

> **Bu dosyanın kuralı:** **NASIL** yapılacağı. `spec.md` onaylanmadan bu dosya yazılmaz.
> Buradaki her başlık `spec.md`'deki bir hikâyeye veya kurala dayanmalı — dayanmıyorsa
> ya spec eksik ya da kapsam kayması var.

---

## Yaklaşım

Seçilen çözümün 3-5 cümlelik özeti. Neden bu yol?

## Değerlendirilen alternatifler

| Alternatif | Artı | Eksi | Karar |
|------------|------|------|-------|
| ... | ... | ... | Seçildi / Elendi — gerekçe |

---

## Arayüzler

### Kotlin tipleri

```kotlin
// src/main/kotlin/dev/acp/<paket>/<Dosya>.kt
```

### plugin.xml uzantıları

```xml
<!-- Yeni extension / action / toolWindow kaydı -->
```

### Ayarlar

Yeni ayar alanı ekleniyorsa: adı, tipi, varsayılanı, gizli mi (`PasswordSafe`) değil mi.

---

## Değişecek Dosyalar

| Dosya | Değişiklik |
|-------|------------|
| `src/main/kotlin/dev/acp/...` | yeni / düzenleme — ne yapıyor |

## Yeniden Kullanılacak Mevcut Kod

Sıfırdan yazmak yerine kullanılacak mevcut sınıflar — kendi kodumuz veya platformun:

- `dev.acp....` — ...
- `com.intellij....` — platform zaten sunuyor, yeniden yazma

---

## Altın Kural Kontrolü

[AGENTS.md](../../AGENTS.md) kurallarına uyum:

- [ ] Yeni çalışma zamanı bağımlılığı eklenmiyor
- [ ] `dev.acp.llm` saf kalıyor (`com.intellij` importu yok)
- [ ] Dile özgü API kullanılmıyor (yalnız `modules.platform`)
- [ ] Kullanıcıya görünen metinler `AcpBundle`'dan geçiyor

## Riskler

| Risk | Etki | Önlem |
|------|------|-------|
| ... | ... | ... |

## Test Stratejisi

- **Saf birim (IDE gerekmez):** hangi sınıflar, hangi kenar durumlar
- **`BasePlatformTestCase`:** hangi davranış
- **Elle doğrulama (`runIde`):** adım adım, gözlenebilir sonucuyla

## Uygulama Sırası

Neyin neden önce geldiği — özellikle riskli parçaların erken doğrulanması.

1. ...
