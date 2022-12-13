package bootcamp.token2;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.flows.*;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class Token2Flow {

    @InitiatingFlow
    @StartableByRPC
    public static class Token2IssuanceFlow extends FlowLogic<String> {

        private final String issuer;
        private final String owner;
        private final int amount;

        public Token2IssuanceFlow(String issuer, String owner, int amount) {
            this.issuer = issuer;
            this.owner = owner;
            this.amount = amount;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {

            //Generate accountinfo & AnonymousParty object for transaction
            AccountInfo issuerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(issuer).get(0).getState().getData();
            AccountInfo ownerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(owner).get(0).getState().getData();


            Party issuerAccount = issuerAccountInfo.getHost();
            Party ownerAccount = ownerAccountInfo.getHost();

            FlowSession ownerSession = initiateFlow(ownerAccountInfo.getHost());

            //grab the notary for transaction building
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            //create a transactionBuilder
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            Token2State tokenState = new Token2State(issuerAccount, ownerAccount , amount);

            transactionBuilder.addOutputState(tokenState);
            transactionBuilder.addCommand(new Token2Contract.Commands.Issue() ,
                    ImmutableList.of(issuerAccount.getOwningKey(), ownerAccount.getOwningKey()));

            transactionBuilder.verify(getServiceHub());

            //sign the transaction with the issuer account hosted on the Initiating node
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder, issuerAccount.getOwningKey());


            //call CollectSignaturesFlow to get the signature from the owner by specifying with issuer key telling CollectSignaturesFlow that issuer has already signed the transaction
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction, Collections.singletonList(ownerSession), Collections.singleton(issuerAccount.getOwningKey())));

            //call FinalityFlow for finality
            SignedTransaction stx = subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(ownerSession)));

            return "One Token2 State issued to "+owner+ " from " + issuer+ " with amount: "+amount +"\ntxId: "+ stx.getId() ;
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class Token2Swap extends FlowLogic<String> {
        private final int amount;
        private final String owner;
        private final String newOwner;

        public Token2Swap(int amount, String owner, String newOwner) {
            this.amount = amount;
            this.owner = owner;
            this.newOwner = newOwner;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {

            //Generate accountinfo & AnonymousParty object for transaction
            AccountInfo ownerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(owner).get(0).getState().getData();
            AccountInfo newOwnerAccountInfo = UtilitiesKt.getAccountService(this).accountInfo(newOwner).get(0).getState().getData();

            Party ownerAccount = ownerAccountInfo.getHost();

            FlowSession ownerSession = initiateFlow(ownerAccountInfo.getHost());

            // Get a reference to the notary.
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Get the vault service for querying for the TokenState
            Vault.Page<Token2State> results = getServiceHub().getVaultService().queryBy(
                    Token2State.class
            );

            // Check if there are any TokenStates in the vault
            if (results.getStates().size() == 0) {
                throw new FlowException("No tokens found in the vault");
            }

            // Get the first TokenState in the vault
            StateAndRef<Token2State> tokenStateAndRef = results.getStates().get(0);
            Token2State tokenState = tokenStateAndRef.getState().getData();

            // Check that the owner of the token is the one specified in the flow
            if (!tokenState.getOwner().equals(ownerAccount)) {
                throw new FlowException("The specified owner is not the owner of the token. owner is: " + tokenState.getOwner().equals(ownerAccount));
            }

            // Check that the specified amount is the same as the amount of the token
            if (tokenState.getAmount() != amount) {
                throw new FlowException("The specified amount does not match the amount of the token");
            }

            // Get the keys of the current owner and new owner
            Party ownerKey = tokenState.getOwner();
            Party newOwnerKey = newOwnerAccountInfo.getHost();

            // Create a new transaction builder
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

            // Create a new TokenState with the new owner
            Token2State newTokenState = new Token2State(
                    tokenState.getIssuer(),
                    newOwnerKey,
                    tokenState.getAmount()
            );

            // Add the output state to the transaction builder
            transactionBuilder.addOutputState(newTokenState);

            // Add a command to the transaction builder to move the token
            transactionBuilder.addCommand(new Token2Contract.Commands.Swap(), ImmutableList.of(ownerKey.getOwningKey(), newOwnerKey.getOwningKey()));

            // Verify the transaction
            transactionBuilder.verify(getServiceHub());

            // Sign the transaction with the owner's key
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

            //call CollectSignaturesFlow to get the signature from the owner by specifying with issuer key telling CollectSignaturesFlow that issuer has already signed the transaction
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction, Collections.singletonList(ownerSession)));

            //call FinalityFlow for finality
            SignedTransaction stx = subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(ownerSession)));

            return "Token2 swap successful. " + amount + " tokens transferred from " + owner + " to " + newOwner + "\ntxId: "+ stx.getId();
        }
    }

    @InitiatedBy(Token2Flow.Token2Swap.class)
    public static class Token2SwapResponder extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public Token2SwapResponder(FlowSession otherSide) {
            this.otherSide = otherSide;
        }

        @Override
        @Suspendable
        public Void call() throws FlowException {

            subFlow(new SignTransactionFlow(otherSide) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Owner can add Custom Logic to validate transaction.
                }
            });
            subFlow(new ReceiveFinalityFlow(otherSide));

            return null;
        }
    }



    @InitiatedBy(Token2Flow.Token2IssuanceFlow.class)
    public static class Token2IssuanceFlowResponder extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public Token2IssuanceFlowResponder(FlowSession otherSide) {
            this.otherSide = otherSide;
        }

        @Override
        @Suspendable
        public Void call() throws FlowException {

            subFlow(new SignTransactionFlow(otherSide) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Owner can add Custom Logic to validate transaction.
                }
            });
            subFlow(new ReceiveFinalityFlow(otherSide));

            return null;
        }
    }
}
