package com.mendelev.mpos

import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.data.local.WorkspaceCodec
import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class FolderLayoutTest {
    private val products=listOf(WorkspaceProduct("a","Кофе","c","2",1,"piece"),WorkspaceProduct("b","Чай","c","3",1,"piece"))
    private val style=FolderAppearance("#DDEBFF","☕",2,120)
    @Test fun appearanceSurvivesRenameProjectionAndReorderWithoutTouchingStock() {
        var items=LayoutEngine.apply(emptyList(),products,setOf("c"),LayoutAction.SaveFolder("f","c","Напитки",style))
        items=LayoutEngine.apply(items,products,setOf("c"),LayoutAction.MoveProduct("a","f"))
        items=LayoutEngine.apply(items,products,setOf("c"),LayoutAction.MoveProduct("b","f"))
        items=LayoutEngine.apply(items,products,setOf("c"),LayoutAction.Reorder("product","b",0))
        assertEquals(listOf("b","a"),items.filter {it.folderId=="f"}.map {it.id})
        items=LayoutEngine.apply(items,products,setOf("c"),LayoutAction.SaveFolder("f","c","Бар"))
        assertEquals(style,items.single {it.type=="folder"}.appearance)
        assertEquals(items,LayoutEngine.project(items,products,setOf("c")))
        val restored=WorkspaceCodec.layout(WorkspaceCodec.layout(items))
        assertEquals(items,restored)
        assertTrue(WorkspaceCodec.layout(items).contains("\"version\":2"))
        val deleted=LayoutEngine.apply(restored,products,setOf("c"),LayoutAction.DeleteFolder("f"))
        assertTrue(deleted.all {it.type=="product" && it.folderId.isEmpty()})
        assertEquals(setOf("a","b"),deleted.map {it.id}.toSet())
    }
    @Test fun legacyLayoutKeepsOriginalDimensionsAndVersion() {
        val old="""{"version":1,"items":[{"type":"folder","id":"f","categoryId":"c","folderId":"","name":"Старое"}]}"""
        val item=WorkspaceCodec.layout(old).single()
        assertEquals(FolderAppearance(),item.appearance)
        assertTrue(WorkspaceCodec.layout(listOf(item)).contains("\"version\":1"))
    }
    @Test fun badAppearanceAndProductStyleAreRejected() {
        listOf(style.copy(color="red"),style.copy(symbol="АБВГ"),style.copy(columns=5),style.copy(tileHeight=0)).forEach {bad->
            try {LayoutEngine.apply(emptyList(),products,setOf("c"),LayoutAction.SaveFolder("f","c","Папка",bad));fail()} catch(_:CatalogFailure) {}
        }
        try {WorkspaceCodec.layout(listOf(LayoutItem("product","a","c",appearance=style)));fail()} catch(_:IllegalArgumentException) {}
        val raw=WorkspaceCodec.layout(listOf(LayoutItem("folder","f","c",name="Папка",appearance=style)))
        listOf(raw.replace("\"columns\":2","\"columns\":\"2\""),raw.replace("\"columns\":2","\"columns\":9"),raw.replace("\"version\":2","\"version\":1")).forEach {
            try {WorkspaceCodec.layout(it);fail()} catch(_:IllegalArgumentException) {}
        }
    }
}
