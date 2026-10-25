package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class HomeLayoutTest {
    private val products=listOf(WorkspaceProduct("p","Чай","a","2",1,"piece"))
    private val cats=listOf("a","b")
    @Test fun moveLeavesHoleAndOccupiedTargetFindsNearestFreeCellWithoutDisplacingOthers() {
        val initial=HomeEngine.project(HomeLayout(),products,cats)
        val moved=HomeEngine.apply(initial,products,cats,HomeAction.Move("category","a",7))
        assertEquals(listOf(7,1),moved.tiles.map {it.slot})
        assertTrue(moved.customized)
        val occupied=HomeEngine.apply(moved,products,cats,HomeAction.Move("category","a",1))
        assertEquals(1,occupied.tiles.single {it.id=="b"}.slot)
        assertEquals(0,occupied.tiles.single {it.id=="a"}.slot)
        assertEquals(occupied,HomeEngine.project(occupied,products,cats+"c"))
    }
    @Test fun removalAdditionAndResetPreserveReferencesAndLimitTwentyTiles() {
        var layout=HomeEngine.apply(HomeLayout(),products,cats,HomeAction.Remove("category","a"))
        layout=HomeEngine.apply(layout,products,cats,HomeAction.Add("product","p"))
        assertEquals(0,layout.tiles.single {it.id=="p"}.slot)
        assertThrows(CatalogFailure::class.java) {HomeEngine.apply(layout,products,cats,HomeAction.Move("product","p",100))}
        assertThrows(CatalogFailure::class.java) {HomeEngine.apply(layout,products,cats,HomeAction.Add("product","gone"))}
        val many=(0..25).map {"c$it"};val full=HomeEngine.project(HomeLayout(),products,many)
        assertEquals(20,full.tiles.size)
        assertThrows(CatalogFailure::class.java) {HomeEngine.apply(full,products,many,HomeAction.Add("product","p"))}
        assertEquals(HomeLayout(),HomeEngine.apply(layout,products,cats,HomeAction.Reset))
    }
    @Test fun malformedSlotsAreRejectedAndDeletedReferenceDoesNotShiftOtherTiles() {
        assertThrows(CatalogFailure::class.java) {HomeEngine.validate(HomeLayout(true,listOf(HomeTile("category","a",0),HomeTile("category","b",0))))}
        val layout=HomeLayout(true,listOf(HomeTile("category","a",0),HomeTile("product","p",9)))
        assertEquals(listOf(HomeTile("product","p",9)),HomeEngine.project(layout,products,listOf("b")).tiles)
    }
    @Test fun categoryReorderChangesOnlySelectedSiblingList() {
        val items=listOf(LayoutItem("product","p","a"),LayoutItem("folder","f","a",name="Папка"),LayoutItem("product","q","b"))
        val ps=products+WorkspaceProduct("q","Другой","b","1",1,"piece")
        val result=LayoutEngine.apply(items,ps,cats.toSet(),LayoutAction.Reorder("product","p",1))
        assertEquals(listOf("f","p","q"),result.map {it.id})
        assertEquals(items.last(),result.last())
    }
}
