package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import java.util.*

@InitiatingFlow
@StartableByRPC
class TransferAssetFlow(val ownerAccountId: UUID, val newOwnerAccountId: UUID, val id: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val newOwnerAccountInfoRef = accountService.accountInfo(newOwnerAccountId) ?: throw IllegalStateException("Can't find account to move to $newOwnerAccountId")
        val newOwnerAccountInfo = newOwnerAccountInfoRef.state.data

        if (newOwnerAccountInfo.host == ourIdentity) return subFlow(TransferAssetToAccountOnSameNodeFlow(ownerAccountId, newOwnerAccountId, id))
        else return subFlow(TransferAssetFlowToAccountOnDifferentNode(ownerAccountId, newOwnerAccountId, id))
    }
}
