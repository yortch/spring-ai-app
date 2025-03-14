
# Spring AI demo

Follow instructions to deploy this demo in Azure.

## Prerequisites

- Azure subscription
- Git
- Maven
- Java 17 or later
- psql client

## Deploy Azure Database for PostgreSQL Flexible Server with pgvector extension

### Sign in to Azure via Azure CLI

Sign in to Azure:

```bash
az login
```

### Set parameter values

The following values are used in subsequent commands to create the database and required resources. Server names need to be globally unique across all of Azure so the `$RANDOM` function is used to create the server name. Change the location as appropriate for your environment.

```bash
export ID=$RANDOM
export LOCATION="eastus2"
export RESOURCE_GROUP="spring-ai-postgresql-rg"
export DB_SERVER_NAME="spring-ai-postgresql-server-$ID"
```

You can limit access by specifying to the PostgreSQL server external IP appropriate IP address values for your environment. Use the public IP address of the computer you're using to restrict access to the server to only your IP address. Initialize the `start` and `end` IP values as follows:

```bash
export PUBLIC_IP=$(curl -s ipinfo.io/ip)
echo "Start IP: $$PUBLIC_IP"
```

**Note** The IP address may change and the corresponding firewall rule needs to be updated accordingly

**Tip** This command should work in most Linux distributions and git bash. If it doesn't work, you can alternatively get your public IP address using [https://whatismyipaddress.com/](https://whatismyipaddress.com/)

#### Create a resource group

Create a resource group with the following command. An Azure resource group is a logical container into which Azure resources are deployed and managed.

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION
```

#### Create an Azure Database for PostgreSQL Server

Use the following command to create a database instance for development purposes. The **burstable** tier is a cost-effective tier for workloads that don't require consistent performance.

```bash
az postgres flexible-server create \
    --resource-group $RESOURCE_GROUP \
    --name $DB_SERVER_NAME \
    --location $LOCATION \
    --tier Burstable \
    --sku-name standard_b1ms \
    --active-directory-auth enabled \
    --public-access $PUBLIC_IP \
    --version 16
```

This command takes a few minutes to complete.

For testing purposes only, run the following command to create a firewall rule to allow access to a wider IP range:

```bash
az postgres flexible-server firewall-rule create \
    --resource-group $RESOURCE_GROUP \
    --name $DB_SERVER_NAME \
    --rule-name allowiprange \
    --start-ip-address 0.0.0.0 \
    --end-ip-address 255.255.255.255
```

#### Grant admin access to your Entra ID

Run the following command to get the `object id` for your Entra ID:

```bash
export USER_OBJECT_ID=$(az ad signed-in-user show \
    --query id \
    --output tsv \
    | tr -d '\r')
```

Run the following command to grant admin access to your Entra ID:

```bash
az postgres flexible-server ad-admin create \
    --resource-group $RESOURCE_GROUP \
    --server-name $DB_SERVER_NAME \
    --object-id $USER_OBJECT_ID \
    --display-name azureuser
```

#### Update allowlist required extensions for pgvector

Before we can enable extensions required by `pgvector`, we need to allow them using this `az` command:

```bash
az postgres flexible-server parameter set \
    --resource-group $RESOURCE_GROUP \
    --server-name $DB_SERVER_NAME \
    --name azure.extensions \
    --value vector,hstore,uuid-ossp
```

### Validate connectivity to your database

Use this command to get the fully qualified host name for your database server:

```bash
export PGHOST=$(az postgres flexible-server show \
    --resource-group $RESOURCE_GROUP \
    --name $DB_SERVER_NAME \
    --query fullyQualifiedDomainName \
    --output tsv \
    | tr -d '\r')
```

Run this command to get access token for your user ID:

```bash
export PGPASSWORD="$(az account get-access-token \
    --resource https://ossrdbms-aad.database.windows.net \
    --query accessToken \
    --output tsv)" 
```

Connect to database using `psql` client with this command:

```bash
psql "host=$PGHOST dbname=postgres user=azureuser sslmode=require"
```

After this rule is created, you can update it by using `az postgres flexible-server firewall-rule update`.

## Compile and test Spring AI RAG Application locally

In this section, we build a retrieval-augmented generation (RAG) application using Spring AI, Azure OpenAI, and `VectorStore` from Spring AI.

### Set up your development environment

Before you start building an AI-powered application, set up your development environment and the required Azure resources.

### Set up your local environment

1. Confirm **Java Development Kit (JDK) 17** (or greater) is installed:

   ```bash
   java -version  # Verify Java installation
   ```

2. Confirm **Maven** is installed:

   ```bash
   mvn -version  # Verify Maven installation
   ```

3. Log in to **Azure** using `az`:

   ```bash
   az login
   ```

### Set up environment variables

Export the following new variables needed for this lab:

```bash
export OPENAI_RESOURCE_NAME=OpenAISpringAI
```

### Deploy the Azure OpenAI models

For our application, you first need to deploy one chat model (gpt-4o) and one embedding model (`text-embedding-ada-002`). To deploy these models, we first need to create an Azure OpenAI resource.

#### Create an Azure OpenAI account

We create the Azure OpenAI account using this Azure CLI command:

```bash
az cognitiveservices account create \
    --resource-group $RESOURCE_GROUP \
    --name $OPENAI_RESOURCE_NAME \
    --kind OpenAI \
    --sku S0 \
    --location $LOCATION \
    --yes
```

#### Deploy an Azure OpenAI chat model

Use the following command to deploy a chat model named `gpt-4o`:

```bash
az cognitiveservices account deployment create \
    --resource-group $RESOURCE_GROUP \
    --name $OPENAI_RESOURCE_NAME \
    --deployment-name gpt-4o \
    --model-name gpt-4o \
    --model-version 2024-11-20 \
    --model-format OpenAI \
    --sku-capacity "15" \
    --sku-name GlobalStandard
```

#### Deploy an Azure OpenAI embedding model

We can now deploy an embedding model named `text-embedding-ada-002` using this command:

```bash
az cognitiveservices account deployment create \
    --resource-group $RESOURCE_GROUP \
    --name $OPENAI_RESOURCE_NAME \
    --deployment-name text-embedding-ada-002 \
    --model-name text-embedding-ada-002 \
    --model-version 2 \
    --model-format OpenAI \
    --sku-capacity 120 \
    --sku-name Standard
```

### Compile Spring AI application

Switch directory to this path:

```bash
cd spring-ai-app
```

You can compile the application skipping tests using this command:

```bash
mvn clean package -DskipTests
```

Expect to see a successful build output.

#### Spring AI configuration

Retrieve the **Azure OpenAI Endpoint** using this command:

```azcli
export AZURE_OPENAI_ENDPOINT=$(az cognitiveservices account show \
    --name $OPENAI_RESOURCE_NAME \
    --resource-group $RESOURCE_GROUP \
    --query "properties.endpoint" \
    --output tsv | tr -d '\r')
```

Retrieve the **Azure OpenAI API Key** using this command:

```azcli
export AZURE_OPENAI_API_KEY=$(az cognitiveservices account keys list \
    --name $OPENAI_RESOURCE_NAME \
    --resource-group $RESOURCE_GROUP \
    --query "key1" \
    --output tsv | tr -d '\r')
```

##### Update application.properties file

Locate the **application.properties.example** file and copy into the **application.properties** file in the **src/main/resources** directory:

```bash
cp application.properties.example src/main/resources/application.properties
```

There are three property values that will be populated using values from these environment variables: **AZURE_OPENAI_API_KEY**, **AZURE_OPENAI_ENDPOINT** and **PGHOST**. If you prefer using different values, you can update this file.

### Test the RAG application

Test the implementation by running:

```bash
mvn spring-boot:run
```

Test the new REST endpoint either from a browser or via a `curl` command:

```bash
curl -G "http://localhost:8080/api/rag" --data-urlencode "query=What is pgvector?"
```

You should see a valid response:

```bash
pgvector is an open-source PostgreSQL extension that enables efficient storage, indexing, 
and querying of vector embeddings within a PostgreSQL database.
```

Next, test asking a question that has content in our vector store:

```bash
curl -G "http://localhost:8080/api/rag" --data-urlencode "query=How does QuestionAnswerAdvisor work in Spring AI?"
```

Expect to see an answer that clearly explains the role of **QuestionAnswerAdvisor** within Spring AI.

### Testing the Blog Generation

Then test the blog generation endpoint using a REST client or `curl`:

```bash
curl -G "http://localhost:8080/api/blog" --data-urlencode "topic=Spring AI Innovation"
```

This should return a blog post that has been generated and iteratively refined through the Evaluator Optimizer Agent process. Because of the review iteration cycle, this request will take longer to complete. Once it completes, expect to see a blog entry about Spring AI Innovation.

## Deploy Spring AI Application to Azure Container Apps

Next, we deploy our Spring AI application to Azure Container Apps for scalable and serverless container hosting.

### Set up environment variables for Azure Container Apps

1. Export a name for the new container app:

   ```bash
   export CONTAINER_APP_NAME=rag-api
   ```

1. Export the name to use as the Managed Identity for the Azure Container App:

    ```bash
    export MANAGED_IDENTITY_NAME=containerappusr
    ```

With these environment values in place, you're now ready to deploy the application into Azure Container Apps.

### Deploy Azure Container Application

To deploy the application, use the following command:

```bash
az containerapp up --name $CONTAINER_APP_NAME \
  --resource-group $RESOURCE_GROUP \
  --environment $ENVIRONMENT \
  --source . \
  --ingress external \
  --target-port 8080 \
  --location $LOCATION
```

To view the logs of your Azure Container App, use the following command:

```bash
az containerapp logs show \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --tail 80
```

Inspect the logs and notice that the application isn't starting successfully due to missing configuration values as expected:

```json
{"TimeStamp": "2025-03-04T19:04:52.7673831+00:00", "Log": "F Caused by: org.postgresql.util.PSQLException: 
FATAL: Azure AD user token for role[AzureAdmin] is neither an AAD_AUTH_TOKENTYPE_APP_USER or an 
AAD_AUTH_TOKENTYPE_APP_OBO token."}
```

To redeploy the application after making changes, you can use the following command:

```bash
az containerapp update \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --source .
```

### Enable managed identity

The Container App needs to be able to authenticate to PostgresSQL server. We use System Assigned Managed Identities for authentication.

To enable system-assigned managed identity for your Azure Container App, use this command:

```bash
az containerapp identity assign \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --system-assigned
```

To get the ID of the system assigned managed identity, use the following command:

```bash
export MANAGED_IDENTITY_ID=$(az containerapp show \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --query 'identity.principalId' \
    --output tsv \
    | tr -d '\r')
echo "Managed Identity ID: $MANAGED_IDENTITY_ID"
```

To authorize the managed identity of your Azure Container App to access the PostgreSQL Flexible Server instance, use the following command:

```bash
az postgres flexible-server ad-admin create \
    --resource-group $RESOURCE_GROUP \
    --server-name $DB_SERVER_NAME \
    --display-name $MANAGED_IDENTITY_NAME \
    --object-id $MANAGED_IDENTITY_ID \
    --type ServicePrincipal
```

### Azure Container App Environment and Secret Configuration

Create a secret for sensitive values:

```bash
az containerapp secret set \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --secrets \
      azure-openai-api-key=$AZURE_OPENAI_API_KEY
```

Next set environment variables:

```bash
az containerapp update \
    --resource-group $RESOURCE_GROUP \
    --name $CONTAINER_APP_NAME \
    --set-env-vars \
      SPRING_AI_AZURE_OPENAI_API_KEY=secretref:azure-openai-api-key \
      SPRING_AI_AZURE_OPENAI_ENDPOINT=${AZURE_OPENAI_ENDPOINT} \
      SPRING_DATASOURCE_USERNAME=${MANAGED_IDENTITY_NAME} \
      SPRING_DATASOURCE_URL=jdbc:postgresql://${PGHOST}/postgres?sslmode=require \
      SPRING_AI_VECTORSTORE_PGVECTOR_SCHEMA_NAME=containerapp
```

We intentionally use a different `pgvector` schema name to avoid conflicts from using the same schema name with a different user.

### Verify the deployment

Get Container App URL:

```bash
export URL=$(az containerapp show \
    --name $CONTAINER_APP_NAME \
    --resource-group $RESOURCE_GROUP \
    --query properties.configuration.ingress.fqdn \
    --output tsv | tr -d '\r')
```

Test Endpoint:

```bash
curl -G "https://$URL/api/rag" --data-urlencode "query=How does QuestionAnswerAdvisor work in Spring AI?"
```

Expect to see a valid response:

```markdown
In the context of **Spring AI**, the **QuestionAnswerAdvisor** operates as a key component for enabling **Retrieval Augmented Generation (RAG)**, which combines user queries with external contextual data to produce accurate and relevant AI responses.
```

Try another question that isn't in our vector store:

```bash
curl -G "https://$URL/api/rag" --data-urlencode "query=What is Vector Search Similarity?"
```

Expect to see a similar valid response:

```markdown
**Vector Search Similarity** refers to the process of comparing and ranking data points (represented as vectors) based on their similarity in a multi-dimensional space. This method is commonly used in applications like information retrieval, recommendation systems, natural language processing, and computer vision.
```

You can also test the blog generation endpoint using a REST client or `curl`:

```bash
curl -G "https://$URL/api/blog" --data-urlencode "topic=Spring AI Innovation"
```

Because of the review iteration cycle, this request will take longer to complete. Once it completes, expect to see a blog entry about Spring AI Innovation.

## Cleanup

After you're done testing, use the following commands to remove resources:

```bash
az group delete \
    --name $RESOURCE_GROUP \
    --yes --no-wait
```
