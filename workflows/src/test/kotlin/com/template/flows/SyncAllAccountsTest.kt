package com.template.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.states.DataState
import com.template.utilities.AccountsHelper
import com.template.utilities.TestBase
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.assertEquals

class SyncAllAccountsTest : TestBase() {

    override val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.template.contracts"),
        TestCordapp.findCordapp("com.template.flows"),
        TestCordapp.findCordapp("com.template.utilities"), // TODO
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
    )))
    private val partyANode = network.createNode()
    private val partyBNode = network.createNode()
    private val partyCNode = network.createNode()

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `accounts are properly synced`() {
        val A1 = partyANode.createAccount(accountName = "A1").state.data
        val B1 = partyBNode.createAccount(accountName = "B1").state.data
        val C1 = partyCNode.createAccount(accountName = "C1").state.data

        partyANode.syncAllAccounts(listOf(partyBNode.getIdentity(), partyCNode.getIdentity()))

        assertEquals(3, partyANode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(3, partyBNode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(3, partyCNode.services.accountService.allAccounts().map { it.state.data }.size)
    }

    @Test
    fun `accounts are only shared once`() {
        val A1 = partyANode.createAccount(accountName = "A1").state.data
        val B1 = partyBNode.createAccount(accountName = "B1").state.data
        val C1 = partyCNode.createAccount(accountName = "C1").state.data

        partyANode.syncAllAccounts(listOf(partyBNode.getIdentity(), partyCNode.getIdentity()))

        val C2 = partyCNode.createAccount(accountName = "C2").state.data

        partyANode.syncAllAccounts(listOf(partyBNode.getIdentity(), partyCNode.getIdentity()))

        assertEquals(4, partyANode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(4, partyBNode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(4, partyCNode.services.accountService.allAccounts().map { it.state.data }.size)
    }

    @Test
    fun `synced accounts can participate in transaction`() {
        val A1 = partyANode.createAccount(accountName = "A1").state.data
        val B1 = partyBNode.createAccount(accountName = "B1").state.data
        partyANode.syncAllAccounts(listOf(partyBNode.getIdentity(), partyCNode.getIdentity()))

        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState = tx1.tx.outputsOfType<DataState>()[0]

        assertDoesNotThrow {
            val tx2 = partyANode.transferAssetGeneric(ownerAccountId = A1.identifier.id, newOwnerAccountId = B1.identifier.id, assetId = dataState.linearId)
        }
    }

    @Test
    fun `sync large number of accounts`() {
        for (i in 1 .. 100) partyANode.createAccount(accountName = "A" + i).state.data
        for (i in 1 .. 100) partyBNode.createAccount(accountName = "B" + i).state.data
        partyANode.syncAllAccounts(listOf(partyBNode.getIdentity(), partyCNode.getIdentity()))

        assertEquals(200, partyANode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(200, partyBNode.services.accountService.allAccounts().map { it.state.data }.size)
        assertEquals(200, partyCNode.services.accountService.allAccounts().map { it.state.data }.size)
    }

    private fun StartedMockNode.issueAssetGeneric(accountId: UUID, data: String): SignedTransaction {
        val future = this.startFlow(IssueAssetGeneric(accountId, data))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.transferAssetGeneric(ownerAccountId: UUID, newOwnerAccountId: UUID, assetId: UniqueIdentifier): SignedTransaction {
        val future = this.startFlow(TransferAssetGeneric(ownerAccountId, newOwnerAccountId, assetId))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.queryAssetByAccount(accountInfo: AccountInfo, assetId: UniqueIdentifier) : DataState? {
        return AccountsHelper.queryAssetByAccount<DataState>(services, accountInfo, assetId)
    }

}