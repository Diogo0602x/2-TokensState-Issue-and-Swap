package bootcamp.token1;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;

public class TokenFlowSync {

    @InitiatingFlow
    @StartableByRPC
    public static class TokenIssuanceFlowSync extends FlowLogic<String> {

        private final String issuer;
        private final String owner;
        private final int amount;

        public TokenIssuanceFlowSync(String issuer, String owner, int amount) {
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
            TokenState tokenState = new TokenState(issuerAccount, ownerAccount , amount);

            transactionBuilder.addOutputState(tokenState);
            transactionBuilder.addCommand(new TokenContract.Commands.Issue() ,
                    ImmutableList.of(issuerAccount.getOwningKey(), ownerAccount.getOwningKey()));

            transactionBuilder.verify(getServiceHub());

            //sign the transaction with the issuer account hosted on the Initiating node
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder, issuerAccount.getOwningKey());

            //call CollectSignaturesFlow to get the signature from the owner by specifying with issuer key telling CollectSignaturesFlow that issuer has already signed the transaction
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction, Collections.singletonList(ownerSession), Collections.singleton(issuerAccount.getOwningKey())));

            //call FinalityFlow for finality
            SignedTransaction stx = subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(ownerSession)));

            return "One Token1 State issued to "+owner+ " from " + issuer+ " with amount: "+amount +"\ntxId: "+ stx.getId() ;
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class TokenSwapSync extends FlowLogic<String> {
        private final int amount;
        private final String owner;
        private final String newOwner;

        public TokenSwapSync(int amount, String owner, String newOwner) {
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
            SignedTransaction selfSignedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

            //call CollectSignaturesFlow to get the signature from the owner by specifying with issuer key telling CollectSignaturesFlow that issuer has already signed the transaction
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(selfSignedTransaction, Collections.singletonList(ownerSession)));

            //call FinalityFlow for finality
            SignedTransaction stx = subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(ownerSession)));

            return "Token1 swap successful. " + amount + " tokens transferred from " + owner + " to " + newOwner + "\ntxId: "+ stx.getId();
        }
    }

    @InitiatedBy(TokenFlowSync.TokenSwapSync.class)
    public static class TokenSwapResponderSync extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public TokenSwapResponderSync(FlowSession otherSide) {
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

    @InitiatedBy(TokenFlowSync.TokenIssuanceFlowSync.class)
    public static class TokenIssuanceFlowResponderSync extends FlowLogic<Void> {

        private final FlowSession otherSide;

        public TokenIssuanceFlowResponderSync(FlowSession otherSide) {
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
