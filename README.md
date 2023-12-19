# Smart Crop Insurance using Corda
Current crop insurance has a lots of drwaback such as manual process, manual inspection and third party interventions due to which farmer does not get insured/promised amount in case loss due to natural calamities. This smart crop insurance solution uses Corda and smart contract and settles automatically using weather data from oracle in condtion of heavy rainfall or droght situation. This application uses latitude and longitute of farmers land and decides percentage of insured amount to be given to Farmers and it automatic. This solution is build using Corda DLT platform which provides trust in the ecosystem.

## Participants
1. Insurance Providers - Who provides the insurance to Farmers to protect their crops from natural calamities.
2. Govt Org/Agricultural Dept - Who approves the insurance products offered by different insurance providers.
3. Farmers - Who enrolls/purchase the insurance policy from different providers.

## Workflow
1. Insurance providers will create a insurance product which contains information about permium, for which crop and insuredAmount they will give to farmer in case of natural calamities.
2. Govt Org or Agriculatural dept or insurance board will approve that perticular product.
3. Now farmer can select that product as insurance policy.
4. Farmer can select products from different insurance providers considering their crop, permium and insuredAmount. That purchased policy contains information/conditions about what percentage of insured amount will be given to farmers in case of crop loss due to rain or drought.
5. **Oracle** will be used to decide rainy days and drought days. Using oracles information insured amount will be given to farmers and it will be automatically done using smart contract and using **Schedulable state** feature of corda.
6. In future, we will add AI to detect/decide the loss percentage. 

## Corda Concepts/features used for solution.
1. STATE, CONTRACT, FLOW
2. ORACLE
3. Reference state
4. Schedulable State
5. Queryable state

## States
1. Product Proposal state - Initially it would be Product proposal from Insurance providers and this proposal goes to the regulator(Govt/Agri Dept/Insurnace Board) to accept it.
2. Product State - It is actual insurance product which will changed from proposal state to product state after acceprance from regulator.
3. Policy State - This state contains information about individual policy and policy holder famers details including their latitude and logitute.

## Contracts
1. Product Contract - verfied for product proposal creation and product creation.
2. Policy Contract - verified for policy creation and at the time claim.

## Flow
1. Create product proposal flow - Insurance provider will initiate it.
2. Accept/Reject Proposal - Regulator (Govt/Agri Dept/insurance board) will initiate.
3. Create Policy - Famer (regulator/Provider will initiate on behalf of Farmer).
4. Auto Claim - It will be automatically invoked by schedulable flow to do automaticall settle using weather information from Oracle.
5. Manual claim - It will invoked by Farmer and will be settled using AI.

## Oracle
1. It will be weather oracle which take external weather history data using can decide wheather its rainy or drought area and as per weather criteria/conditions provided in Product, insured amount will be deposited to farmers automatically.

## Running the nodes
Open a terminal and go to the project root directory and type: (to deploy the nodes using bootstrapper)

./gradlew clean deployNodes

Then type: (to run the nodes)

./build/nodes/runnodes

## Running the client (API)
Please note : (No UI only API endpoints)

run a 'insurance-webserver-1.0.jar' jar from clients/build/libs

