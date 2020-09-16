package com.template.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.workflows.accountService
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * SyncAllAccounts will sync all AccountInfo states between a list of parties in nodeList.
 */
@InitiatingFlow
@StartableByRPC
class SyncAllAccounts(val nodeList: List<Party>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val sessions = (nodeList).map { initiateFlow(it) }
        sessions.forEach { it.send(nodeList + ourIdentity) }
        subFlow(SyncMyAccounts(nodeList))
    }
}

@InitiatingFlow
@InitiatedBy(SyncAllAccounts::class)
class SyncAllAccountsResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val parties = counterpartySession.receive<List<Party>>().unwrap { data -> data }
        subFlow(SyncMyAccounts(parties - ourIdentity))
    }
}


/**
 * SyncMyAccounts will share all AccountInfo states on the initiating node with the nodes listed in nodeList.
 */
@InitiatingFlow
class SyncMyAccounts(val nodeList: List<Party>) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val sessions = nodeList.map { initiateFlow(it) }
        val allAccountsRefs = accountService.ourAccounts()
        val transactions = allAccountsRefs.map { serviceHub.validatedTransactions.getTransaction(it.ref.txhash) }
        sessions.forEach {
            it.send(transactions)
        }
    }
}

@InitiatingFlow
@InitiatedBy(SyncMyAccounts::class)
class SyncMyAccountsResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transactions = counterpartySession.receive<List<SignedTransaction>>().unwrap { data -> data }
        serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, transactions)
    }
}