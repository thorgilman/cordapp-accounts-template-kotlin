package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.template.states.AssetState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria

@InitiatingFlow
@StartableByRPC
class QueryAssetByAccountFlow(val accountInfo: AccountInfo, val assetId: UniqueIdentifier) : FlowLogic<AssetState?>() {

    @Suspendable
    override fun call(): AssetState? {
        val participatingAccountCriteria = VaultQueryCriteria().withExternalIds(externalIds = listOf(accountInfo.identifier.id))
        val linearStateQueryCriteria = LinearStateQueryCriteria(linearId = listOf(assetId))
        val queryResults = serviceHub.vaultService.queryBy<AssetState>(participatingAccountCriteria and linearStateQueryCriteria)
        if (queryResults.states.isEmpty()) return null
        else return queryResults.states[0].state.data
    }
}