<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Bootcamp Accounts CorDapp

This project is the template we will use as a basis for developing a complete CorDapp 
during today's bootcamp. Our CorDapp will allow the issuance of tokens to accounts onto the ledger.


## Set up

1. Download and install a JDK 8 JVM (minimum supported version 8u131)
2. Download and install IntelliJ Community Edition (supported versions 2017.x and 2018.x)
3. Download the bootcamp-cordapp repository:

       git clone https://github.com/corda/bootcamp-cordapp/tree/bootcamp-accounts
       
4. Open IntelliJ. From the splash screen, click `Import Project`, select the `bootcamp—
cordapp` folder and click `Open`
5. Select `Import project from external model > Gradle > Next > Finish`
6. Click `File > Project Structure…` and select the Project SDK (Oracle JDK 8, 8u131+)

    i. Add a new SDK if required by clicking `New…` and selecting the JDK’s folder

7. Open the `Project` view by clicking `View > Tool Windows > Project`
8. Run the test in `src/test/java/java_bootcamp/ProjectImportedOKTest.java`. It should pass!

## What we'll be building

Our CorDapp will have three parts:

### The TokenState

States define shared facts on the ledger. Our state, TokenState, will define a
token. In this CorDapp, issuer and owner will be accounts. They will be represented
by AnonymousParty class instead of Party class.
It will have the following structure:

    -------------------
    |                 |
    |   TokenState    |
    |                 |
    |   - issuer      |
    |   - owner       |
    |   - amount      |
    |                 |
    -------------------

### The TokenContract

Contracts govern how states evolve over time. Our contract, TokenContract,
will define how TokenStates evolve. It will only allow the following type of
TokenState transaction:

    -------------------------------------------------------------------------------------
    |                                                                                   |
    |    - - - - - - - - - -                                     -------------------    |
    |                                              ▲             |                 |    |
    |    |                 |                       | -►          |   TokenState    |    |
    |            NO             -------------------     -►       |                 |    |
    |    |                 |    |      Issue command       -►    |   - issuer      |    |
    |          INPUTS           |     signed by issuer     -►    |   - owner       |    |
    |    |                 |    -------------------     -►       |   - amount > 0  |    |
    |                                              | -►          |                 |    |
    |    - - - - - - - - - -                       ▼             -------------------    |
    |                                                                                   |
    -------------------------------------------------------------------------------------

              No inputs             One issue command,                One output,
                                 issuer is a required signer       amount is positive

To do so, TokenContract will impose the following constraints on transactions
involving TokenStates:

* The transaction has no input states
* The transaction has one output state
* The transaction has one command
* The output state is a TokenState
* The output state has a positive amount
* The command is an Issue command
* The command lists the TokenState's issuer account and owner account as a required signer

### The TokenIssueFlow

Flows automate the process of updating the ledger. 
We will see how PartyA's issuer will issue a token to PartyB's owner.

Our flow, TokenIssueFlow, will automate the following steps:

            Issuer                  Owner                  Notary
              |                       |                       |
       Chooses a notary
              |                       |                       |
        Starts building
         a transaction                |                       |
              |
        Adds the output               |                       |
          TokenState
              |                       |                       |
           Adds the
         Issue command                |                       |
              |
         Verifies the                 |                       |
          transaction
              |                       |                       |
          Signs the
         transaction                  |                       |
              |
              |----------------------------------------------►|
              |                       |                       |
                                                         Notarises the
              |                       |                   transaction
                                                              |
              |◀----------------------------------------------|
              |                       |                       |
         Records the
         transaction                  |                       |
              |
              |----------------------►|                       |
                                      |
              |                  Records the                  |
                                 transaction
              |                       |                       |
              ▼                       ▼                       ▼

## Running our CorDapp

Normally, you'd interact with a CorDapp via a client or webserver. So we can
focus on our CorDapp, we'll be running it via the node shell instead.

Once you've finished the CorDapp's code, run it with the following steps:

* Build a test network of nodes by opening a terminal window at the root of
  your project and running the following command:

    * Windows:   `gradlew.bat deployNodes`
    * macOS:     `./gradlew deployNodes`

* Start the nodes by running the following command:

    * Windows:   `build\nodes\runnodes.bat`
    * macOS:     `build/nodes/runnodes`

#### Step 1 : Create and Share Account

To issue tokens, we will create two accounts issuerAccount on PartyA node, and ownerAccount on PartyB node.
issuerAccount will issue a token to ownerAccount.
The very first thing is to create these accounts on respective nodes and share account info wih the 
counterparties. This is achieved by running below flows. 

Run below flow on PartyA's node.
This will create an issuerAccount on PartyA's node and share it with PartyB

    start CreateAndShareAccountFlow  accountName : issuerAccount , partyToShareAccountInfoToList : PartyB

Run below flow on PartyB's node.    
This will create an ownerAccount on PartyB's node and share it with PartyA

    start CreateAndShareAccountFlow  accountName : ownerAccount , partyToShareAccountInfoToList : PartyA


#### Step 2 : Issue Token to Accounts

Run the below flow on PartyA's node.
Run the below flow to issue token from issuerAccount on PartyA's node to ownerAccount on PartyB's node.

    start TokenIssuanceFlow issuer : issuerAccount, owner : ownerAccount , amount : 10
