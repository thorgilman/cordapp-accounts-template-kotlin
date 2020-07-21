package com.template.states

import com.template.contracts.AssetContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty

// *********
// * State *
// *********
@BelongsToContract(AssetContract::class)
data class AssetState(val data: String,
                   override val owner: AbstractParty,
                   override val participants: List<AbstractParty> = listOf(owner),
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, OwnableState {

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(AssetContract.Commands.Transfer(), this.copy(owner = newOwner))
    }
}
