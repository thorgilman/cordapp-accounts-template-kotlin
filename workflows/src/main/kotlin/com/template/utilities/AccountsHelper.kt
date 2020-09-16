package com.template.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort

object AccountsHelper {

    @Suspendable
    inline fun <reified T: ContractState>queryAssetByAccount(serviceHub: ServiceHub, accountInfo: AccountInfo, assetId: UniqueIdentifier): T? {
        val participatingAccountCriteria = QueryCriteria.VaultQueryCriteria().withExternalIds(externalIds = listOf(accountInfo.identifier.id))
        val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(assetId))
        val queryResults = serviceHub.vaultService._queryBy(participatingAccountCriteria and linearStateQueryCriteria, PageSpecification(), Sort(emptySet()), T::class.java)
        if (queryResults.states.isEmpty()) return null
        // if (results > 1) ...
        else return queryResults.states[0].state.data
    }

}