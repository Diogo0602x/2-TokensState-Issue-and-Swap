package bootcamp.token2;

import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class Token2Contract implements Contract {

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        CommandWithParties<Token2Contract.Commands> command = requireSingleCommand(tx.getCommands(), Token2Contract.Commands.class);

        List<ContractState> inputs = tx.getInputStates();
        List<ContractState> outputs = tx.getOutputStates();

        if (command.getValue() instanceof Token2Contract.Commands.Issue) {
            requireThat(req -> {
                req.using("Transaction must have no input states.", inputs.isEmpty());
                req.using("Transaction must have exactly one output.", outputs.size() == 1);
                req.using("Output must be a TokenState.", outputs.get(0) instanceof Token2State);
                Token2State output = (Token2State) outputs.get(0);
                req.using("Issuer Account must be required singer.", command.getSigners().contains(output.getIssuer().getOwningKey()));
                req.using("Owner Account must be required singer.", command.getSigners().contains(output.getOwner().getOwningKey()));
                req.using("Amount must be positive.", output.getAmount() > 0);
                return null;
            });
        }

        else if (command.getValue() instanceof Token2Contract.Commands.Swap) {
            requireThat(req -> {
                req.using("Transaction must have no input states.", inputs.isEmpty());
                req.using("Transaction must have exactly one output.", outputs.size() == 1);
                req.using("Output must be a TokenState.", outputs.get(0) instanceof Token2State);
                Token2State output = (Token2State) outputs.get(0);
                req.using("New Owner Account must be required singer.", command.getSigners().contains(output.getOwner().getOwningKey()));
                req.using("Amount must be positive.", output.getAmount() > 0);
                return null;
            });
        } else {
            throw new IllegalArgumentException("Unrecognized command");
        }
    }

    public interface Commands extends CommandData {
        class Issue implements Commands { }

        class Swap implements Commands {}

        //class Transfer implements Commands{}

    }
}