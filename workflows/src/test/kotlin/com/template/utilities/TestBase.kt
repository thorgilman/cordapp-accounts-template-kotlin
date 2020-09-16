package com.template.utilities

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import java.util.*

abstract class TestBase {

    abstract val network: MockNetwork

    protected fun StartedMockNode.createAccount(accountName: String): StateAndRef<AccountInfo> {
        val future = this.startFlow(CreateAccount(accountName))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    protected fun StartedMockNode.shareAccount(accountInfo: StateAndRef<AccountInfo>, party: Party) {
        val future = this.startFlow(ShareAccountInfo(accountInfo, listOf(party)))
        network.runNetwork()
        future.getOrThrow()
    }

    protected fun StartedMockNode.syncAllAccounts(nodeList: List<Party>) {
        val future = this.startFlow(SyncAllAccounts(nodeList))
        network.runNetwork()
        future.getOrThrow()
    }

    protected fun StartedMockNode.queryForAccountInfo(accountId: UUID): AccountInfo? {
        val results = this.services.accountService.accountInfo(accountId)
        return if (results==null) null else results.state.data
    }

    protected fun StartedMockNode.getIdentity(): Party {
        return this.info.legalIdentities.single()
    }

}