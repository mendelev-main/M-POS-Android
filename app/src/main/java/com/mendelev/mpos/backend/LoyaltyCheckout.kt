package com.mendelev.mpos.backend

import android.content.Context
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.sale.SaleDiscount
import com.mendelev.mpos.domain.workspace.CartAction
import kotlinx.coroutines.CancellationException
import java.util.UUID

internal object LoyaltyCheckout {
    suspend fun verify(context: Context,work: WorkspaceSnapshot,discounts: Map<String,SaleDiscount>): WorkspaceSnapshot {
        val selection=work.cart.loyalty ?: return work
        try {
            val s=LoyaltyServices.settings(context)
            check(selection.target==s.loyaltyTarget() && selection.datasetId==s.datasetId) {"Подключение изменилось. Выберите клиента заново"}
            val fresh=try {LoyaltyServices.api(context).profile(selection.customer.id)}
                catch(e:CancellationException) {throw e}
                catch(e:Exception) {if(selection.selected.isEmpty()) return work else throw e}
            if(selection.selected.isEmpty()) {
                if(fresh==selection) return work
                val repo=WorkspaceRepository(CatalogDatabase.get(context))
                repo.cart(CartAction.SetLoyalty(fresh,work.cartRevision),UUID.randomUUID().toString(),work.cart.sessionId)
                return repo.snapshot()
            }
            check(fresh.customer.active && fresh.customer.consentVersion==selection.customer.consentVersion) {"Участие клиента изменилось. Обновите профиль"}
            check(selection.selected.all {id->fresh.programs.any {it.id==id && it.active && it.rewards>0}}) {"Подарок больше недоступен. Обновите профиль клиента"}
            val chosen=fresh.copy(selected=selection.selected)
            val cart=work.cart.copy(loyalty=chosen)
            check(LoyaltyRules.allocation(work.cart,discounts)==LoyaltyRules.allocation(cart,discounts)) {"Правила подарка изменились. Обновите профиль клиента"}
            val verified=chosen.copy(verified=LoyaltyRules.fingerprint(cart,discounts))
            if(verified==selection) return work
            val repo=WorkspaceRepository(CatalogDatabase.get(context))
            repo.cart(CartAction.SetLoyalty(verified,work.cartRevision),UUID.randomUUID().toString(),work.cart.sessionId)
            return repo.snapshot()
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {throw CatalogFailure("loyalty",e.message ?: "Не удалось проверить подарок. Повторите проверку или продолжите без подарка")}
    }
}
