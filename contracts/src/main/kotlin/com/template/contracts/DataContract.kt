package com.template.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class DataContract : Contract {
    companion object {
        const val ID = "com.template.contracts.AssetContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when(command.value) {
            is Commands.Issue -> requireThat{}
            is Commands.Transfer -> requireThat{}
            is Commands.Share -> requireThat{}
        }
    }

    interface Commands : CommandData {
        class Issue : Commands
        class Transfer : Commands
        class Share : Commands
    }
}