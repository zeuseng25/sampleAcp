package dev.acp.agent

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Model ile IDE arasındaki yol sözleşmesi: yollar proje köküne göredir.
 *
 * Araç yürütücüsü (008) ve ilişki araması (010) **aynı** kuralı kullanmalı —
 * biri yolu bulup öteki bulamazsa model aynı dosya için iki farklı cevap alır.
 */
internal object ProjeYollari {

    /**
     * Projenin içerik kökleri.
     *
     * **`project.basePath` KULLANILMAZ.** Çok modüllü projelerde dosyalar
     * `basePath` altında olmayabilir; ayrıca `basePath` null olabilir. İçerik
     * kökleri IDE'nin dosyaları gerçekten nerede aradığını söyler — arama
     * (`iterateContent`) da zaten onları geziyor, yol çözümü de aynı kaynağı
     * kullanmalı. (Bu tutarsızlığı testler yakaladı.)
     */
    fun kokler(project: Project): List<VirtualFile> {
        val icerik = ProjectRootManager.getInstance(project).contentRoots
        if (icerik.isNotEmpty()) return icerik.toList()
        // Yedek: içerik kökü tanımlı değilse proje dizini.
        return listOfNotNull(
            project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) },
        )
    }

    fun goreli(project: Project, dosya: VirtualFile): String {
        for (kok in kokler(project)) {
            val onek = kok.path + "/"
            if (dosya.path.startsWith(onek)) return dosya.path.removePrefix(onek)
        }
        return dosya.path
    }

    /**
     * Proje kökleri dışındaki yollar reddedilir (spec 008 T44).
     *
     * `..` içeren yol baştan reddediliyor: kapsam dışına çıkma denemesidir.
     * Model kötü niyetli olmasa da yanlış yol üretebilir, ve sonuç aynıdır.
     */
    fun bul(project: Project, yol: String): VirtualFile? {
        if (yol.split('/', '\\').any { it == ".." }) return null

        for (kok in kokler(project)) {
            val hedef = kok.findFileByRelativePath(yol) ?: continue
            if (!hedef.path.startsWith(kok.path)) continue
            if (!hedef.isDirectory) return hedef
        }
        return null
    }
}
