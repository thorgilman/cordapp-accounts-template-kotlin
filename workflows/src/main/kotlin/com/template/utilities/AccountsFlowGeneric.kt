package com.template.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount
import com.template.flows.ShareDataGeneric
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException
import java.security.PublicKey
import java.util.*
import kotlin.NoSuchElementException

/**
 * AccountsFlowGeneric is an abstract flow that can be subclassed to more easily create Accounts enabled flows.
 *
 * By abstracting away the need to manage account signing keys, a developer can retreive an AccountInfo state once and use that state to identify the account throughout the entirety of the flow.
 * In the beginning of a flow, a developer would call addAccountInfo() for each relevant account to retreive the AccountInfo state(s).
 * For each AccountInfo added, a signing key is also generated and stored for future use.
 * When a developer needs to use the account's party or key, they simply need to call accountInfo.getAccountParty() or accountInfo.getAccountSignature().
 *
 * This class also simplifies the signing and finalization process for accounts with signTransactionAndFinalize().
 * signTransactionAndFinalize() will handle which signatures need to come from the initiating node and which need to be collected from other nodes.
 */
abstract class AccountsFlowGeneric<out T> : FlowLogic<T>() {

    // TODO: Goal: Abstract away signing keys

    private val accountMap = HashMap<AccountInfo, AnonymousParty>()

    // When you first retrieve the AccountInfo, keys will be generated and stored for future use
    @Suspendable
    protected fun addAccountInfo(accountId: UUID): AccountInfo {
        val accountInfoRef = accountService.accountInfo(accountId) ?: throw IllegalStateException("Can't find account $accountId")
        val accountInfo = accountInfoRef.state.data
        if (accountMap[accountInfo] != null) return accountInfo
        val anonParty = subFlow(RequestKeyForAccount(accountInfo))
        accountMap[accountInfo] = anonParty
        return accountInfo
    }

    @Suspendable
    protected fun addAccountInfo(accountName: String): AccountInfo {
        val accountInfoRefList = accountService.accountInfo(accountName)
        if (accountInfoRefList.isEmpty()) throw IllegalStateException("Cannot find account $accountName")
        if (accountInfoRefList.size > 1) throw IllegalStateException("More than one account found with name $accountName")
        val accountInfo = accountInfoRefList[0].state.data
        if (accountMap[accountInfo] != null) return accountInfo
        val anonParty = subFlow(RequestKeyForAccount(accountInfo))
        accountMap[accountInfo] = anonParty
        return accountInfo
    }

    @Suspendable
    protected fun addAccountInfos(accountIdList: List<UUID>): List<AccountInfo> {
        return accountIdList.map { addAccountInfo(it) }
    }
    // TODO: Create addAccountInfos(accountIdList: List<String>)

    @Suspendable
    protected fun AccountInfo.getAccountParty(): AnonymousParty{
        return accountMap[this] ?: throw IllegalArgumentException("Cannot find key for account $this")
    }

    @Suspendable
    protected fun AccountInfo.getAccountKey(): PublicKey{
        return this.getAccountParty().owningKey
    }

    @Suspendable
    protected fun List<AccountInfo>.getAccountParties(): List<AnonymousParty>{
        return this.map { it.getAccountParty() }
    }

    @Suspendable
    protected fun List<AccountInfo>.getAccountKeys(): List<PublicKey>{
        return this.map { it.getAccountKey() }
    }

    @Suspendable
    protected fun signTransactionAndFinalize(txBuilder: TransactionBuilder, accountInfos: List<AccountInfo>): SignedTransaction {

        val (hostNodeAccounts, otherNodeAccounts) = accountInfos.partition { it.host == ourIdentity }

        // Sign with keys on host node
        val ptx = serviceHub.signInitialTransaction(txBuilder, hostNodeAccounts.map { it.getAccountKey() })

        val sessions = otherNodeAccounts.map{it.host}.distinct().map {initiateFlow(it)}
        // Must specify we've already signed with all the keys from the initiating node (otherwise Corda will expect the node's key to have signed)
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions, hostNodeAccounts.map { it.getAccountKey() }))

        return subFlow(FinalityFlow(stx, sessions))
    }

}