package com.template.states

import com.template.contracts.DataContract
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty

// *********
// * State *
// *********
@BelongsToContract(DataContract::class)
data class DataState(val data: String,
                     val owner: AbstractParty,
                   override val participants: List<AbstractParty> = listOf(owner),
                   override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    fun withNewOwner(newOwner: AbstractParty): DataState {
        return copy(owner = newOwner, participants = participants - owner + newOwner)
    }

    fun withAdditionalParticipants(particpantsList: List<AbstractParty>): DataState {
        return copy(participants = particpantsList + participants)
    }


}
