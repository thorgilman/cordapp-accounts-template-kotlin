package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.template.states.AssetState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class TransferAssetToAccountOnSameNodeFlow(val ownerAccountId: UUID, val newOwnerAccountId: UUID, val id: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    // TODO: progressTracker
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // Lookup the AccountInfo objects using the account ids
        val ownerAccountInfoRef = accountService.accountInfo(ownerAccountId) ?: throw IllegalStateException("Can't find account to move from $ownerAccountId")
        val newOwnerAccountInfoRef = accountService.accountInfo(newOwnerAccountId) ?: throw IllegalStateException("Can't find account to move to $newOwnerAccountId")
        val ownerAccountInfo = ownerAccountInfoRef.state.data
        val newOwnerAccountInfo = newOwnerAccountInfoRef.state.data

        // Request a new signing key for the accounts
        val ownerParty = subFlow(RequestKeyForAccount(ownerAccountInfo))
        val newOwnerParty = subFlow(RequestKeyForAccount(newOwnerAccountInfo))

        val queryResults = serviceHub.vaultService.queryBy<AssetState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))
        val inputStateRef = queryResults.states.single()
        val inputState = inputStateRef.state.data

        val outputStateAndCommand = inputState.withNewOwner(newOwnerParty)
        val outputState = outputStateAndCommand.ownableState
        val command = Command(outputStateAndCommand.command, listOf(ownerParty.owningKey, newOwnerParty.owningKey)) // Both the owner and newOwner are required signers

        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(inputStateRef)
                .addOutputState(outputState)
                .addCommand(command)

        val stx = serviceHub.signInitialTransaction(txBuilder, listOf(ownerParty.owningKey, newOwnerParty.owningKey)) // Must put both signing keys
        txBuilder.verify(serviceHub)

        return subFlow(FinalityFlow(stx, emptyList()))
    }
}