package com.template.flows

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.template.states.DataState
import com.template.utilities.AccountsHelper
import com.template.utilities.SyncAllAccounts
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


class ShareDataGenericTest : TestBase() {

    override val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.template.contracts"),
        TestCordapp.findCordapp("com.template.flows"),
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
    fun `share to same node`() {

        val A1 = partyANode.createAccount(accountName = "A1").state.data
        val B1 = partyANode.createAccount(accountName = "B1").state.data

        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState = tx1.tx.outputsOfType<DataState>()[0]

        assertEquals(dataState, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(null, partyANode.queryAssetByAccount(B1, dataState.linearId))

        val tx2 = partyANode.shareAssetGeneric(ownerAccountId = A1.identifier.id, accountIdList = listOf(B1.identifier.id), assetId = dataState.linearId)
        val dataState2 = tx2.tx.outputsOfType<DataState>()[0]

        assertEquals(dataState2, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(dataState2, partyANode.queryAssetByAccount(B1, dataState.linearId))
    }

    @Test
    fun `share to diff node`() {

        val acctA1Ref = partyANode.createAccount(accountName = "A1")
        val acctB1Ref = partyBNode.createAccount(accountName = "B1")
        val A1 = acctA1Ref.state.data
        val B1 = acctB1Ref.state.data

        partyBNode.shareAccount(acctB1Ref, partyANode.getIdentity())

        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState = tx1.tx.outputsOfType<DataState>()[0]
        assertEquals(dataState, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(null, partyBNode.queryAssetByAccount(B1, dataState.linearId))

        val tx2 = partyANode.shareAssetGeneric(ownerAccountId = A1.identifier.id, accountIdList = listOf(B1.identifier.id), assetId = dataState.linearId)
        val dataState2 = tx2.tx.outputsOfType<DataState>()[0]

        assertEquals(dataState2, partyANode.queryAssetByAccount(A1, dataState.linearId))
        assertEquals(dataState2, partyBNode.queryAssetByAccount(B1, dataState.linearId))
    }

    @Test
    fun `share to same node & diff nodes`() {

        val acctA1Ref = partyANode.createAccount(accountName = "A1")
        val acctA2Ref = partyANode.createAccount(accountName = "A2")
        val acctB1Ref = partyBNode.createAccount(accountName = "B1")
        val acctB2Ref = partyBNode.createAccount(accountName = "B2")
        val acctC1Ref = partyCNode.createAccount(accountName = "C1")

        val A1 = acctA1Ref.state.data
        val A2 = acctA2Ref.state.data
        val B1 = acctB1Ref.state.data
        val B2 = acctB2Ref.state.data
        val C1 = acctC1Ref.state.data

        // TODO: Try account sync
        // TODO: Create my own account sync?
        // Share relevant accounts
        partyANode.shareAccount(acctA1Ref, partyBNode.getIdentity())
        partyANode.shareAccount(acctA2Ref, partyBNode.getIdentity())
        partyBNode.shareAccount(acctB1Ref, partyANode.getIdentity())
        partyBNode.shareAccount(acctB2Ref, partyANode.getIdentity())
        partyCNode.shareAccount(acctC1Ref, partyANode.getIdentity())
        partyCNode.shareAccount(acctC1Ref, partyBNode.getIdentity())
        partyANode.shareAccount(acctA1Ref, partyCNode.getIdentity())
        partyANode.shareAccount(acctA2Ref, partyCNode.getIdentity())
        partyBNode.shareAccount(acctB1Ref, partyCNode.getIdentity())
        partyBNode.shareAccount(acctB2Ref, partyCNode.getIdentity())


        val tx1 = partyANode.issueAssetGeneric(accountId = A1.identifier.id, data = "My data")
        val dataState1 = tx1.tx.outputsOfType<DataState>()[0]
        val stateId = dataState1.linearId

        assertEquals(dataState1, partyANode.queryAssetByAccount(A1, stateId))
        assertEquals(null, partyANode.queryAssetByAccount(A2, stateId))
        assertEquals(null, partyBNode.queryAssetByAccount(B1, stateId))
        assertEquals(null, partyBNode.queryAssetByAccount(B2, stateId))
        assertEquals(null, partyCNode.queryAssetByAccount(C1, stateId))

        val tx2 = partyANode.shareAssetGeneric(ownerAccountId = A1.identifier.id, accountIdList = listOf(A2.identifier.id, B1.identifier.id, B2.identifier.id, C1.identifier.id), assetId = stateId)
        val dataState2 = tx2.tx.outputsOfType<DataState>()[0]

        assertEquals(dataState2, partyANode.queryAssetByAccount(A1, stateId))
        assertEquals(dataState2, partyANode.queryAssetByAccount(A2, stateId))
        assertEquals(dataState2, partyBNode.queryAssetByAccount(B1, stateId))
        assertEquals(dataState2, partyBNode.queryAssetByAccount(B2, stateId))
        assertEquals(dataState2, partyCNode.queryAssetByAccount(C1, stateId))
    }


    private fun StartedMockNode.issueAssetGeneric(accountId: UUID, data: String): SignedTransaction {
        val future = this.startFlow(IssueAssetGeneric(accountId, data))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.shareAssetGeneric(ownerAccountId: UUID, accountIdList: List<UUID>, assetId: UniqueIdentifier): SignedTransaction {
        val future = this.startFlow(ShareDataGeneric(ownerAccountId, accountIdList, assetId))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.queryAssetByAccount(accountInfo: AccountInfo, assetId: UniqueIdentifier) : DataState? {
        return AccountsHelper.queryAssetByAccount<DataState>(services, accountInfo, assetId)
    }



}