package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.DataContract
import com.template.states.DataState
import com.template.utilities.AccountsFlowGeneric
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/*
This flow will issue an DataState to a specified account on the initiating node.
 */

@InitiatingFlow
@StartableByRPC
class IssueAssetGeneric(val accountId: UUID, val data: String) : AccountsFlowGeneric<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        val accountInfo = addAccountInfo(accountId)

        val txBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(DataState(data, accountInfo.getAccountParty()))
                .addCommand(Command(DataContract.Commands.Issue(), accountInfo.getAccountKey()))

        txBuilder.verify(serviceHub)

        return signTransactionAndFinalize(txBuilder, listOf(accountInfo))
    }
}