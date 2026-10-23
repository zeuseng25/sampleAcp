package dev.acp.llm.agent

/**
 * Ajanın sistem yönergesi.
 *
 * **Bu bir ayrıntı değil, özelliğin kendisidir.** T10 ölçümü (2026-09-04):
 * aynı model, aynı araçlar, aynı görev —
 *
 * | | Yönergesiz | Yönergeyle |
 * |---|---|---|
 * | Adım | 20 (sınırda) | 3 |
 * | Token | 204.490 | 4.683 |
 * | Düzenleme | **0** | **9** |
 *
 * Yönergesiz koşuda model yerleri 5. adımda buldu, sonra **aynı aramayı 12 kez
 * tekrarladı** ve hiç düzenleme üretmeden adım sınırına dayandı.
 *
 * Bu yüzden metin ayrı bir sabit ve içeriği testle sabitlenmiş durumda
 * (`AjanYonergesiTest`). İyi niyetli bir kısaltma özelliği çalışmaz hale
 * getirebilir ve bu **hiçbir davranış testinde görünmez**.
 *
 * Saf: IDE'ye bağlı hiçbir şey bilmez.
 */
internal object AjanYonergesi {

    /**
     * Altı kural; ilk beşi T10'da gözlenen bir başarısızlığa karşılık geliyor:
     *
     * 1. Tekrarlanan arama — modelin kilitlendiği yer
     * 2. Gereksiz dosya okuma — bağlamı şişirip token yakan davranış
     * 3. Bulunca hemen düzenleme — model buluyor ama harekete geçmiyordu
     * 4. Satırın tamamı — `eski` metin birebir eşleşmezse düzenleme atılır
     * 5. Bitince dur — model bitirmeyi bilmiyordu
     * 6. Doğrulama yalnızca hata bildirilince (spec 009) — komut onay istiyor;
     *    model boşa sorarsa kullanıcı her seferinde "Atla"ya basmak zorunda kalır
     */
    const val METIN: String =
        "Sen bir kod düzenleme asistanısın. Kurallar:\n" +
            "1. Aynı aramayı TEKRARLAMA. Bir arama sonucu aldıysan o bilgi elindedir.\n" +
            "2. Arama sonucu sana dosya yolunu ve TAM SATIRI verir. Düzenleme için bu " +
            "yeterlidir; dosyayı okuman GEREKMEZ.\n" +
            "3. Yerleri bulur bulmaz her biri için dosya_duzenle çağır. Hepsini tek " +
            "mesajda çağırabilirsin.\n" +
            "4. 'eski' alanına arama sonucundaki satırın TAMAMINI yaz (baştaki boşluklar " +
            "dahil). Birebir eşleşmezse düzenleme uygulanmaz.\n" +
            "5. Bütün düzenlemeleri kaydettikten sonra kısa bir özet yaz ve DUR.\n" +
            "6. dogrula'yı yalnızca kullanıcı bir derleme veya test hatası bildirdiyse çağır. " +
            "Hata metni görevde zaten varsa çağırma, onu kullan. Komut diskteki kodu derler; " +
            "kaydettiğin düzenlemeler henüz diskte değildir. Hata kodda değilse (JDK sürümü, " +
            "bağımlılık indirme, ağ) kodu değiştirme; durumu kullanıcıya söyle."
}
