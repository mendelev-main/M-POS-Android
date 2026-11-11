package com.mendelev.mpos

import com.mendelev.mpos.domain.loyalty.LoyaltyProgram
import com.mendelev.mpos.feature.loyalty.loyaltyProgramProgress
import org.junit.Assert.*
import org.junit.Test

class CustomerProgramProgressTest {
    private val p=LoyaltyProgram("p","Кофе",5,emptyList(),emptyList())
    @Test fun fractionalProgressIsBoundedAndDoesNotInventRewardEntitlement() {
        assertEquals(.5f,loyaltyProgramProgress(p.copy(progress="2.5")),0.000001f)
        assertEquals(0f,loyaltyProgramProgress(p.copy(progress="-1")),0f)
        assertEquals(1f,loyaltyProgramProgress(p.copy(progress="10")),0f)
        assertEquals(0,p.copy(progress="10").rewards)
        assertEquals(0f,loyaltyProgramProgress(p.copy(required=0)),0f)
    }
    @Test fun availableGiftShowsCompleteBarEvenAfterServerResetsProgress() {
        assertEquals(1f,loyaltyProgramProgress(p.copy(progress="0",rewards=1)),0f)
    }
}
