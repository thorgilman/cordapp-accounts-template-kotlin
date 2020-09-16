package com.template.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
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
import java.util.*
import kotlin.test.assertEquals

class TransferAssetGenericTest : TestBase() {

    override val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.template.contracts"),
        TestCordapp.findCordapp("com.template.flows"),
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
    )))
    private val partyANode = network.createNode()
    private val partyBNode = network.createNode()

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `transfer to same node`() {

        val A1 = partyANode.createAccount(accountName = "A1").state.data
        val A2 = partyANode.createAccount(accountName = "A2").state.data

        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState = tx1.tx.outputsOfType<DataState>()[0]
        assertEquals(dataState, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(null, partyANode.queryAssetByAccount(A2, dataState.linearId))

        val tx2 = partyANode.transferAssetGeneric(ownerAccountId = A1.identifier.id, newOwnerAccountId = A2.identifier.id, assetId = dataState.linearId)
        val dataState2 = tx2.tx.outputsOfType<DataState>()[0]

        assertEquals(null, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(dataState2, partyANode.queryAssetByAccount(A2, dataState.linearId))
    }

    @Test
    fun `transfer to diff node`() {

        val A1Ref = partyANode.createAccount(accountName = "A1")
        val B1Ref = partyBNode.createAccount(accountName = "B1")
        val A1 = A1Ref.state.data
        val B1 = B1Ref.state.data

        partyBNode.shareAccount(B1Ref, partyANode.getIdentity())

        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState = tx1.tx.outputsOfType<DataState>()[0]
        assertEquals(dataState, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(null, partyBNode.queryAssetByAccount(B1, dataState.linearId))

        val tx2 = partyANode.transferAssetGeneric(ownerAccountId = A1.identifier.id, newOwnerAccountId = B1.identifier.id, assetId = dataState.linearId)
        val dataState2 = tx2.tx.outputsOfType<DataState>()[0]

        assertEquals(null, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(dataState2, partyBNode.queryAssetByAccount(B1, dataState.linearId))
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