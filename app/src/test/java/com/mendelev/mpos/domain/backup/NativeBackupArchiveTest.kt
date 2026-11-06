package com.mendelev.mpos.domain.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class NativeBackupArchiveTest {
    private fun directory()=Files.createTempDirectory("native-backup-test").toFile()
    private fun reject(block: ()->Unit) {try {block();fail("Expected refusal")} catch(_:BackupFailure) {}}
    private val id="00000000-0000-0000-0000-000000000001.jpg"
    private fun fixture(dir: File): File {
        val db=File(dir,"db").apply {writeBytes(byteArrayOf(0,1,2,3,127,-1))}
        val photo=File(dir,"photo").apply {writeBytes(byteArrayOf(8,9,10))}
        return File(dir,"copy.mposnative").also {NativeBackupArchive.write(it,NativeBackupFiles(db,mapOf(id to photo),123))}
    }
    private fun entries(file: File): LinkedHashMap<String,ByteArray> = linkedMapOf<String,ByteArray>().also {all->ZipInputStream(file.inputStream()).use {z->while(true) {val e=z.nextEntry ?: break;all[e.name]=z.readBytes();z.closeEntry()}}}
    private fun zip(values: Map<String,ByteArray>): ByteArray=ByteArrayOutputStream().also {out->ZipOutputStream(out).use {z->values.forEach {(n,b)->z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry()}}}.toByteArray()
    @Test fun exactBytesRoundtripAndFrozenManifest() {
        val dir=directory()
        try {
            val archive=fixture(dir);val input=archive.readBytes();val target=File(dir,"read").apply {mkdir()}
            val restored=NativeBackupArchive.read(input.inputStream(),target)
            assertEquals(123L,restored.createdAt);assertArrayEquals(File(dir,"db").readBytes(),restored.database.readBytes());assertArrayEquals(File(dir,"photo").readBytes(),restored.photos.getValue(id).readBytes())
            assertArrayEquals(input,archive.readBytes());assertEquals(64,NativeBackupArchive.sha256(archive).length)
            assertFalse(entries(archive).getValue("manifest.json").decodeToString().contains("${dir.absolutePath}"))
        } finally {dir.deleteRecursively()}
    }
    @Test fun versionDuplicateKeysAndUnknownManifestFieldsRefused() {
        val dir=directory()
        try {
            val base=entries(fixture(dir));val raw=base.getValue("manifest.json").decodeToString()
            for(text in listOf(raw.replace("\"version\":1","\"version\":2"),raw.replace("\"schema\":9","\"schema\":10"),raw.replace("\"format\":","\"version\":1,\"format\":"),raw.replace("\"format\":","\"unknown\":0,\"format\":"))) {
                val data=LinkedHashMap(base).apply {put("manifest.json",text.toByteArray())};val target=Files.createTempDirectory(dir.toPath(),"read").toFile()
                reject {NativeBackupArchive.read(zip(data).inputStream(),target)}
            }
        } finally {dir.deleteRecursively()}
    }
    @Test fun tamperMissingExtraAndTraversalNeverEscapeStaging() {
        val dir=directory()
        try {
            val base=entries(fixture(dir))
            val mutations=listOf(LinkedHashMap(base).apply {put("database.sqlite",byteArrayOf(9,8,7,6,5,4))},LinkedHashMap(base).apply {remove("photos/$id")},LinkedHashMap(base).apply {put("../escaped",byteArrayOf(1))},LinkedHashMap(base).apply {put("manifest.json",getValue("manifest.json").decodeToString().replace("photos/$id","../escaped").toByteArray())})
            mutations.forEach {data->reject {NativeBackupArchive.read(zip(data).inputStream(),Files.createTempDirectory(dir.toPath(),"read").toFile())}}
            assertFalse(File(dir,"escaped").exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun boundsWrongSizesAndTruncatedEntryRefused() {
        val dir=directory()
        try {
            val archive=fixture(dir);val base=entries(archive)
            for(size in listOf("0","268435457","9223372036854775808","6.0")) {
                val data=LinkedHashMap(base).apply {put("manifest.json",getValue("manifest.json").decodeToString().replace("\"size\":6","\"size\":$size").toByteArray())}
                reject {NativeBackupArchive.read(zip(data).inputStream(),Files.createTempDirectory(dir.toPath(),"read").toFile())}
            }
            try {NativeBackupArchive.read(archive.readBytes().copyOf(90).inputStream(),Files.createTempDirectory(dir.toPath(),"read").toFile());fail()} catch(_:Exception) {}
            reject {NativeBackupArchive.write(File(dir,"bad"),NativeBackupFiles(File(dir,"db"),mapOf("../evil" to File(dir,"photo")),1))}
        } finally {dir.deleteRecursively()}
    }
    @Test fun changedSourceAndCancellationCannotProduceConfirmedArchive() {
        val dir=directory()
        try {
            val db=File(dir,"db").apply {writeText("original")};var n=0
            reject {NativeBackupArchive.write(File(dir,"changed"),NativeBackupFiles(db,emptyMap(),1)) {if(++n==2) db.writeText("changed!")}}
            try {NativeBackupArchive.write(File(dir,"cancelled"),NativeBackupFiles(db,emptyMap(),1)) {throw java.util.concurrent.CancellationException()};fail()} catch(_:java.util.concurrent.CancellationException) {}
            val target=File(dir,"nonempty").apply {mkdir();File(this,"preserve").writeText("keep")}
            reject {NativeBackupArchive.read(fixture(dir).inputStream(),target)};assertEquals("keep",File(target,"preserve").readText())
        } finally {dir.deleteRecursively()}
    }
}
