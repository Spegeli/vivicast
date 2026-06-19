package com.vivicast.core.epg

interface XmltvImporter {
    suspend fun import(sourceName: String, content: suspend () -> Sequence<String>): XmltvImportBatch
}
