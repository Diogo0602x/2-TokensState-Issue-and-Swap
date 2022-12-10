package bootcamp.token2;


import bootcamp.token2.Token2Contract;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/* Our state, defining a shared fact on the ledger.
 * See src/main/java/examples/ArtState.java for an example. */
@BelongsToContract(Token2Contract.class)
public class Token2State implements ContractState{

    private final AnonymousParty issuer;
    private final AnonymousParty owner;
    private final int amount;

    public Token2State(AnonymousParty issuer, AnonymousParty owner, int amount) {
        this.issuer = issuer;
        this.owner = owner;
        this.amount = amount;
    }

    public AnonymousParty getIssuer() {
        return issuer;
    }

    public AnonymousParty getOwner() {
        return owner;
    }

    public int getAmount() {
        return amount;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(issuer,owner);
    }
}