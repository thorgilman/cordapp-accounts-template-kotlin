package com.template

import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.internal.accountService
import com.template.flows.*
import com.template.states.AssetState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.apache.shiro.authc.Account
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals


class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
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
    fun `asset is issued to account`() {
        val acctARef = partyANode.createAccount(accountName = "acctA")
        val acctA = acctARef.state.data

        val tx1 = partyANode.issueAsset(accountId = acctA.identifier.id, data = "My data")
        val assetState = tx1.tx.outputsOfType<AssetState>()[0]
        assertEquals(assetState, partyANode.queryAssetByAccount(acctA, assetState.linearId))
    }

    @Test
    fun `account infos are properly shared`() {
        val acctARef = partyANode.createAccount(accountName = "acctA")
        val acctA = acctARef.state.data

        partyANode.shareAccount(acctARef, partyBNode.getIdentity())
        assertEquals(acctA, partyBNode.queryForAccountInfo(acctA.identifier.id))
    }

    @Test
    fun `transfer to different node`() {

        val acctARef = partyANode.createAccount(accountName = "acctA")
        val acctBRef = partyBNode.createAccount(accountName = "acctB")
        val acctA = acctARef.state.data
        val acctB = acctBRef.state.data

        partyANode.shareAccount(acctARef, partyBNode.getIdentity())
        partyBNode.shareAccount(acctBRef, partyANode.getIdentity())

        val tx1 = partyANode.issueAsset(accountId = acctA.identifier.id, data = "My data")
        val assetState = tx1.tx.outputsOfType<AssetState>()[0]
        assertEquals(assetState, partyANode.queryAssetByAccount(acctA, assetState.linearId))
        assertEquals(null, partyBNode.queryAssetByAccount(acctB, assetState.linearId))

        val tx2 = partyANode.transferAssetDifferentNode(ownerAccountId = acctA.identifier.id, newOwnerAccountId = acctB.identifier.id, assetId = assetState.linearId)
        val assetState2 = tx2.tx.outputsOfType<AssetState>()[0]
        assertEquals(null, partyANode.queryAssetByAccount(acctA, assetState.linearId))
        assertEquals(assetState2, partyBNode.queryAssetByAccount(acctB, assetState.linearId))
    }

    @Test
    fun `transfer to same node`() {

        val acct1Ref = partyANode.createAccount(accountName = "acct1")
        val acct2Ref = partyANode.createAccount(accountName = "acct2")
        val acct1 = acct1Ref.state.data
        val acct2 = acct2Ref.state.data

        val tx1 = partyANode.issueAsset(accountId = acct1.identifier.id, data = "My data")
        val assetState = tx1.tx.outputsOfType<AssetState>()[0]
        assertEquals(assetState, partyANode.queryAssetByAccount(acct1, assetState.linearId))
        assertEquals(null, partyANode.queryAssetByAccount(acct2, assetState.linearId))

        val tx2 = partyANode.transferAssetSameNode(ownerAccountId = acct1.identifier.id, newOwnerAccountId = acct2.identifier.id, assetId = assetState.linearId)
        val assetState2 = tx2.tx.outputsOfType<AssetState>()[0]

        assertEquals(null, partyANode.queryAssetByAccount(acct1, assetState.linearId))
        assertEquals(assetState2, partyANode.queryAssetByAccount(acct2, assetState.linearId))
    }


    @Test
    fun `transfer to different node and same node`() {

        val acctARef = partyANode.createAccount(accountName = "acctA")
        val acctB1Ref = partyBNode.createAccount(accountName = "acctB1")
        val acctB2Ref = partyBNode.createAccount(accountName = "acctB2")

        val acctA = acctARef.state.data
        val acctB1 = acctB1Ref.state.data
        val acctB2 = acctB2Ref.state.data

        partyANode.shareAccount(acctARef, partyBNode.getIdentity())
        partyBNode.shareAccount(acctB1Ref, partyANode.getIdentity())
        partyBNode.shareAccount(acctB2Ref, partyANode.getIdentity())

        val tx1 = partyANode.issueAsset(accountId = acctA.identifier.id, data = "My data")
        val assetState = tx1.tx.outputsOfType<AssetState>()[0]

        val tx2 = partyANode.transferAsset(ownerAccountId = acctA.identifier.id, newOwnerAccountId = acctB1.identifier.id, assetId = assetState.linearId)
        val assetState2 = tx2.tx.outputsOfType<AssetState>()[0]

        val tx3 = partyBNode.transferAsset(ownerAccountId = acctB1.identifier.id, newOwnerAccountId = acctB2.identifier.id, assetId = assetState.linearId)
        val assetState3 = tx3.tx.outputsOfType<AssetState>()[0]

        assertEquals(null, partyANode.queryAssetByAccount(acctA, assetState.linearId))
        assertEquals(null, partyBNode.queryAssetByAccount(acctB1, assetState.linearId))
        assertEquals(assetState3, partyBNode.queryAssetByAccount(acctB2, assetState.linearId))
    }










    private fun StartedMockNode.createAccount(accountName: String): StateAndRef<AccountInfo> {
        val future = this.startFlow(CreateAccount(accountName))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.shareAccount(accountInfo: StateAndRef<AccountInfo>, party: Party) {
        val future = this.startFlow(ShareAccountInfo(accountInfo, listOf(party)))
        network.runNetwork()
        val transaction = future.getOrThrow()
    }

    private fun StartedMockNode.issueAsset(accountId: UUID, data: String): SignedTransaction {
        val future = this.startFlow(IssueAssetFlow(accountId, data))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.transferAsset(ownerAccountId: UUID, newOwnerAccountId: UUID, assetId: UniqueIdentifier): SignedTransaction {
        val future = this.startFlow(TransferAssetFlow(ownerAccountId, newOwnerAccountId, assetId))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.transferAssetDifferentNode(ownerAccountId: UUID, newOwnerAccountId: UUID, assetId: UniqueIdentifier): SignedTransaction {
        val future = this.startFlow(TransferAssetFlowToAccountOnDifferentNode(ownerAccountId, newOwnerAccountId, assetId))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.transferAssetSameNode(ownerAccountId: UUID, newOwnerAccountId: UUID, assetId: UniqueIdentifier): SignedTransaction {
        val future = this.startFlow(TransferAssetToAccountOnSameNodeFlow(ownerAccountId, newOwnerAccountId, assetId))
        network.runNetwork()
        val transactionState = future.getOrThrow()
        return transactionState
    }

    private fun StartedMockNode.queryForAccountInfo(accountId: UUID): AccountInfo? {
        val results = this.services.accountService.accountInfo(accountId)
        return if (results==null) null else results.state.data
    }

    private fun StartedMockNode.queryAssetByAccount(accountInfo: AccountInfo, assetId: UniqueIdentifier) : AssetState? {
        val future = this.startFlow(QueryAssetByAccountFlow(accountInfo, assetId))
        network.runNetwork()
        val assetState = future.getOrThrow()
        return assetState
    }

    fun StartedMockNode.getIdentity(): Party {
        return this.info.legalIdentities.single()
    }

}