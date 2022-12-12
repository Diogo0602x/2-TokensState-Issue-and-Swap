package bootcamp.token1;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TokenFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class TokenIssuanceFlow extends FlowLogic<String> {

        private final String issuer;
        private final String owner;
        private final int amount;

        public TokenIssuanceFlow(String issuer, String owner, int amount) {
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

            AnonymousParty issuerAccount = subFlow(new RequestKeyForAccount(issuerAccountInfo));
            AnonymousParty ownerAccount = subFlow(new RequestKeyForAccount(ownerAccountInfo));

            FlowSession ownerSession = initiateFlow(ownerAccountInfo.getHost());

            //grab the notary for transaction building
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            //create a transactionBuilder
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            TokenState tokenState = new TokenState(issuerAccount, ownerAccount , amount);

            transactionBuilder.addOutputState(tokenState);
            transactionBuilder.addCommand(new TokenContract.Commands.Issue() ,
                    ImmutableList.of(issuerAccount.getOwningKey(), ownerAccount.getOwningKey()));

            transactionBuilder.verify(getServiceHub());

            //sign the transaction with the issuer account hosted on the Initiating node
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder, issuerAccount.getOwningKey());


            //call CollectSignaturesFlow to get the signature from the owner by specifying with issuer key telling CollectSignaturesFlow that issuer has already signed the transaction
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction, Arrays.asList(ownerSession), Collections.singleton(issuerAccount.getOwningKey())));

            //call FinalityFlow for finality
            SignedTransaction stx = subFlow(new FinalityFlow(fullySignedTx, Arrays.asList(ownerSession)));

            return "One Token1 State issued to "+owner+ " from " + issuer+ " with amount: "+amount +"\ntxId: "+ stx.getId() ;
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class TokenSwap extends FlowLogic<String> {
        private final int amount;
        private final String owner;
        private final String newOwner;

        public TokenSwap(int amount, String owner, String newOwner) {
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

            AnonymousParty ownerAccount = subFlow(new RequestKeyForAccount(ownerAccountInfo));

            FlowSession newOwnerSession = initiateFlow(newOwnerAccountInfo.getHost());

            // Get the vault service for querying for the TokenState
            Vault.Page<TokenState> results = getServiceHub().getVaultService().queryBy(
                    TokenState.class
            );

            // Check if there are any TokenStates in the vault
            if (results.getStates().size() == 0) {
                throw new FlowException("No tokens found in the vault");
            }

            // Get the first TokenState in the vault
            StateAndRef<TokenState> tokenStateAndRef = results.getStates().get(0);
            TokenState tokenState = tokenStateAndRef.getState().getData();

            // Check that the owner of the token is the one specified in the flow
            if (!tokenState.getOwner().equals(owner)) {
                throw new FlowException("The specified owner is not the owner of the token");
            }

            // Check that the specified amount is the same as the amount of the token
            if (tokenState.getAmount() != amount) {
                throw new FlowException("The specified amount does not match the amount of the token");
            }

            // Get the keys of the current owner and new owner
            AnonymousParty ownerKey = tokenState.getOwner();
            AnonymousParty newOwnerKey = subFlow(new RequestKeyForAccount(newOwnerAccountInfo));

            // Create a new transaction builder
            TransactionBuilder transactionBuilder = new TransactionBuilder();

            // Create a new TokenState with the new owner
            TokenState newTokenState = new TokenState(
                    tokenState.getIssuer(),
                    newOwnerKey,
                    tokenState.getAmount()
            );

            // Add the output state to the transaction builder
            transactionBuilder.addOutputState(newTokenState);

            // Add a command to the transaction builder to move the token
            transactionBuilder.addCommand(new TokenContract.Commands.Swap(), ImmutableList.of(ownerKey.getOwningKey(), newOwnerKey.getOwningKey()));

            // Verify the transaction
            transactionBuilder.verify(getServiceHub());

            // Sign the transaction with the owner's key
            SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

            // Call the FinalityFlow to finalize the transaction
            subFlow(new FinalityFlow(signedTransaction, (FlowSession) Collections.emptyList(), (FlowSession) Arrays.asList(newOwnerSession)));

            return "Token1 swap successful. " + amount + " tokens transferred from " + owner + " to " + newOwner + ".";
        }
    }

    @InitiatedBy(TokenFlow.TokenSwap.class)
    public static class TokenSwapResponder extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public TokenSwapResponder(FlowSession otherSide) {
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



    @InitiatedBy(TokenFlow.TokenIssuanceFlow.class)
    public static class TokenIssuanceFlowResponder extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public TokenIssuanceFlowResponder(FlowSession otherSide) {
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
