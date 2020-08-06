package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.template.contracts.AssetContract
import com.template.states.AssetState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.Vault.StateStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*


@InitiatingFlow
@StartableByRPC
class TransferAssetFlowToAccountOnDifferentNode(val ownerAccountId: UUID, val newOwnerAccountId: UUID, val id: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

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

        val queryResults = serviceHub.vaultService.queryBy<AssetState>(LinearStateQueryCriteria(linearId = listOf(id)))
        val inputStateRef = queryResults.states.single()
        val inputState = inputStateRef.state.data

        val outputStateAndCommand = inputState.withNewOwner(newOwnerParty)
        val outputState = outputStateAndCommand.ownableState
        val command = Command(outputStateAndCommand.command, listOf(ownerParty.owningKey, newOwnerParty.owningKey)) // Both the owner and newOwner are required signers

        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(inputStateRef)
                .addOutputState(outputState)
                .addCommand(command)

        val ptx = serviceHub.signInitialTransaction(txBuilder, ownerParty.owningKey) // Must explicitly put the Accounts key, otherwise the node's public key will be used
        txBuilder.verify(serviceHub)

        val targetSession = initiateFlow(newOwnerAccountInfo.host) // Initiate flow session with host node
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(targetSession), listOf(ownerParty.owningKey))) // Must specify we've already signed with our Account key (otherwise it assumes the node's public key should be used)
        return subFlow(FinalityFlow(stx, targetSession))
    }
}

@InitiatedBy(TransferAssetFlowToAccountOnDifferentNode::class)
class TransferAssetFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {}
        }
        val txWeJustSigned = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(counterpartySession, txWeJustSigned.id))
    }
}