package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByKey
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.template.contracts.AssetContract
import com.template.states.AssetState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.lang.IllegalArgumentException
import java.util.*

/*
This flow will issue an AssetState to a specified account on the initiating node.
 */

@InitiatingFlow
@StartableByRPC
class IssueAssetFlow(val accountId: UUID, val data: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        // Get the AccountInfo object from the accounts UUID
        // Note: This could also be done by passing in the account name instead of the account's UUID
        val accountInfo = accountService.accountInfo(accountId) ?: throw IllegalStateException("Cannot find account with UUID: $accountId on this node")
        //if (accountInfo.state.data.host != ourIdentity) throw IllegalArgumentException() // TODO: Is this necessary???

        // Request a new signing key for the account
        val accountParty = subFlow(RequestKeyForAccount(accountInfo.state.data))

        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(AssetState(data, accountParty))
                .addCommand(Command(AssetContract.Commands.Issue(), accountParty.owningKey))

        txBuilder.verify(serviceHub)

        val tx = serviceHub.signInitialTransaction(txBuilder, accountParty.owningKey)
        return subFlow(FinalityFlow(tx, emptyList()))
    }
}