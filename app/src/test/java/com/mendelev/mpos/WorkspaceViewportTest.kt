package com.mendelev.mpos

import com.mendelev.mpos.feature.workspace.workspaceRowHeightPx
import org.junit.Assert.*
import org.junit.Test

class WorkspaceViewportTest {
    @Test fun twentyTilesFitFourRowsAtDifferentTabletDensities() {
        // 13-inch 2500x1560 display and smaller/resized available content areas.
        listOf(480f,613.3f,1100f,1331f).forEach {height->
            listOf(1f,1.5f,2f).forEach {density->
                val gap=12*density;val padding=4*density
                val row=workspaceRowHeightPx(height,4,padding,gap)
                val used=row*4+gap*3+padding*2
                assertTrue("No hidden fifth row / pixel overflow",used<=height)
                assertTrue("Unused height is less than one pixel per row",height-used<4)
            }
        }
    }
    @Test fun portraitTwentyTilesFitFiveRowsAndShortViewportCanShrinkBelowOldMinimum() {
        val row=workspaceRowHeightPx(500f,5,4f,12f)
        assertEquals(88f,row,0f)
        assertEquals(496f,row*5+4*12+8,0f)
        assertTrue(row<128f)
    }
}
