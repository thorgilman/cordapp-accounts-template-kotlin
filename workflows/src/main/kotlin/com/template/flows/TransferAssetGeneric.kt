package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.DataContract
import com.template.utilities.AccountsFlowGeneric
import com.template.states.DataState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class TransferAssetGeneric(val ownerAccountId: UUID, val newOwnerAccountId: UUID, val id: UniqueIdentifier) : AccountsFlowGeneric<SignedTransaction>() {

    // TODO: progressTracker
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        val ownerAccountInfo = addAccountInfo(ownerAccountId)
        val newOwnerAccountInfo = addAccountInfo(newOwnerAccountId)

        val queryResults = serviceHub.vaultService.queryBy<DataState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))
        val inputStateRef = queryResults.states.single()
        val inputState = inputStateRef.state.data

        val outputState = inputState.withNewOwner(newOwnerAccountInfo.getAccountParty())
        val command = Command(DataContract.Commands.Transfer(), listOf(ownerAccountInfo, newOwnerAccountInfo).getAccountKeys()) // Both the owner and newOwner are required signers

        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(inputStateRef)
                .addOutputState(outputState)
                .addCommand(command)

        txBuilder.verify(serviceHub)

        return signTransactionAndFinalize(txBuilder, listOf(ownerAccountInfo, newOwnerAccountInfo))
    }
}

@InitiatedBy(TransferAssetGeneric::class)
class TransferAssetGenericResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {}
        }
        val txWeJustSigned = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(counterpartySession, txWeJustSigned.id))
    }
}