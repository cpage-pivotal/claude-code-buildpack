# GCP Artifact Registry Deployment Guide

This guide explains how to deploy the `claude-code-cf-wrapper` Maven artifacts to GCP Artifact Registry with public access.

## Prerequisites

1. **GCP Account**: You need an active GCP account
2. **gcloud CLI**: Install and configure the gcloud CLI
   ```bash
   # Install gcloud CLI (if not already installed)
   # Visit: https://cloud.google.com/sdk/docs/install
   
   # Authenticate
   gcloud auth login
   gcloud auth application-default login
   ```

3. **Maven**: Ensure Maven is installed (version 3.6+)
   ```bash
   mvn --version
   ```

## Step 1: Create Artifact Registry Repository

First, create a Maven repository in GCP Artifact Registry:

```bash
# Set your project ID
export PROJECT_ID="your-gcp-project-id"
gcloud config set project $PROJECT_ID

# Set the repository name and location
export REPO_NAME="maven-public"
export LOCATION="us-central1"

# Create the repository
gcloud artifacts repositories create $REPO_NAME \
    --repository-format=maven \
    --location=$LOCATION \
    --description="Public Maven repository for claude-code-cf-wrapper"
```

## Step 2: Make Repository Publicly Accessible

To allow public read access without authentication:

```bash
# Grant allUsers read access to the repository
gcloud artifacts repositories add-iam-policy-binding $REPO_NAME \
    --location=$LOCATION \
    --member=allUsers \
    --role=roles/artifactregistry.reader

# Verify the policy
gcloud artifacts repositories get-iam-policy $REPO_NAME \
    --location=$LOCATION
```

This allows anyone to download artifacts without authentication, but only you can upload.

## Step 3: Update POM Configuration

Update the `distributionManagement` section in `pom.xml` with your actual values:

```xml
<distributionManagement>
    <repository>
        <id>artifact-registry</id>
        <name>GCP Artifact Registry</name>
        <url>artifactregistry://us-central1-maven.pkg.dev/YOUR_PROJECT_ID/maven-public</url>
    </repository>
</distributionManagement>
```

Replace:
- `YOUR_PROJECT_ID` with your actual GCP project ID
- `us-central1` with your chosen region
- `maven-public` with your repository name

## Step 4: Configure Maven Authentication

Configure Maven to authenticate with GCP Artifact Registry:

```bash
# Add the Artifact Registry plugin to your Maven settings
gcloud artifacts print-settings mvn \
    --repository=$REPO_NAME \
    --location=$LOCATION \
    --project=$PROJECT_ID
```

This command will output configuration that you should add to your `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>artifact-registry</id>
      <configuration>
        <httpConfiguration>
          <get>
            <usePreemptive>true</usePreemptive>
          </get>
          <head>
            <usePreemptive>true</usePreemptive>
          </head>
        </httpConfiguration>
      </configuration>
    </server>
  </servers>
</settings>
```

**Note**: With gcloud CLI authenticated, Maven will automatically use your credentials via Application Default Credentials (ADC).

## Step 5: Deploy the Artifacts

Navigate to the java-wrapper directory and deploy:

```bash
cd java-wrapper

# Clean and build the project
mvn clean install

# Deploy to GCP Artifact Registry
mvn deploy
```

### One-Command Deployment

You can also do everything in one command:

```bash
mvn clean deploy
```

## Step 6: Verify Deployment

After deployment, verify the artifacts are available:

```bash
# List artifacts in the repository
gcloud artifacts packages list \
    --repository=$REPO_NAME \
    --location=$LOCATION

# View specific package details
gcloud artifacts versions list \
    --package=org.tanzu.claudecode:claude-code-cf-wrapper \
    --repository=$REPO_NAME \
    --location=$LOCATION
```

You can also view in the GCP Console:
```
https://console.cloud.google.com/artifacts/maven/${LOCATION}/${REPO_NAME}
```

## Using the Published Artifacts

### For Public Users (No Authentication Required)

Once published with public access, users can add your artifact to their `pom.xml`:

```xml
<repositories>
    <repository>
        <id>gcp-artifact-registry</id>
        <name>GCP Artifact Registry</name>
        <url>https://us-central1-maven.pkg.dev/YOUR_PROJECT_ID/maven-public</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.tanzu.claudecode</groupId>
        <artifactId>claude-code-cf-wrapper</artifactId>
        <version>1.1.1</version>
    </dependency>
</dependencies>
```

**Note**: For public repositories, use `https://` instead of `artifactregistry://` in the repository URL.

## Complete Deployment Script

Here's a complete script to automate the entire process:

```bash
#!/bin/bash

# Configuration
PROJECT_ID="your-gcp-project-id"
REPO_NAME="maven-public"
LOCATION="us-central1"

echo "=== GCP Artifact Registry Deployment ==="
echo "Project: $PROJECT_ID"
echo "Repository: $REPO_NAME"
echo "Location: $LOCATION"
echo

# Step 1: Set project
echo "Setting GCP project..."
gcloud config set project $PROJECT_ID

# Step 2: Create repository (if it doesn't exist)
echo "Creating Artifact Registry repository..."
if gcloud artifacts repositories describe $REPO_NAME --location=$LOCATION &>/dev/null; then
    echo "Repository already exists, skipping creation..."
else
    gcloud artifacts repositories create $REPO_NAME \
        --repository-format=maven \
        --location=$LOCATION \
        --description="Public Maven repository for claude-code-cf-wrapper"
fi

# Step 3: Make repository public
echo "Making repository publicly accessible..."
gcloud artifacts repositories add-iam-policy-binding $REPO_NAME \
    --location=$LOCATION \
    --member=allUsers \
    --role=roles/artifactregistry.reader

# Step 4: Update POM (manual step reminder)
echo
echo "⚠️  MANUAL STEP REQUIRED:"
echo "Update pom.xml distributionManagement URL to:"
echo "  artifactregistry://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
echo
read -p "Press Enter once you've updated pom.xml..."

# Step 5: Build and deploy
echo "Building and deploying Maven artifacts..."
cd "$(dirname "$0")"
mvn clean deploy

# Step 6: Verify
echo
echo "=== Deployment Complete ==="
echo "View your artifacts at:"
echo "https://console.cloud.google.com/artifacts/maven/${LOCATION}/${REPO_NAME}?project=${PROJECT_ID}"
echo
echo "Public repository URL (for consumers):"
echo "https://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
```

Save this as `deploy-to-gcp.sh` and run:

```bash
chmod +x deploy-to-gcp.sh
./deploy-to-gcp.sh
```

## Troubleshooting

### Authentication Issues

If you encounter authentication errors:

```bash
# Re-authenticate
gcloud auth login
gcloud auth application-default login

# Verify authentication
gcloud auth list
gcloud config list
```

### Permission Errors

If you get permission errors during upload:

```bash
# Ensure you have the Artifact Registry Writer role
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="user:YOUR_EMAIL@example.com" \
    --role="roles/artifactregistry.writer"
```

### Maven Build Errors

If Maven can't resolve dependencies:

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
mvn clean install -U
```

### Verify Public Access

To test public access, try downloading without authentication:

```bash
# Download artifact metadata (should work without auth)
curl -I https://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}/org/tanzu/claudecode/claude-code-cf-wrapper/maven-metadata.xml
```

## Cost Considerations

- **Storage**: GCP Artifact Registry charges for storage (~$0.10/GB/month)
- **Network egress**: Charges apply for downloads outside GCP regions
- **Operations**: Read/write operations have minimal costs
- **Free tier**: 0.5 GB storage and some egress is included in the free tier

For current pricing, see: https://cloud.google.com/artifact-registry/pricing

## Security Best Practices

1. **Public read access only**: Never make write access public
2. **Version control**: Use semantic versioning for releases
3. **Snapshots**: Consider a separate repository for snapshots
4. **Access logs**: Enable audit logging to track usage
5. **Regular updates**: Keep dependencies and plugins up to date

## Next Steps

1. Update `pom.xml` with your actual GCP project details
2. Run the deployment script or manual commands
3. Share the public repository URL with users
4. Consider setting up CI/CD for automated deployments

## Additional Resources

- [GCP Artifact Registry Documentation](https://cloud.google.com/artifact-registry/docs)
- [Maven Repository Configuration](https://maven.apache.org/guides/introduction/introduction-to-repositories.html)
- [Artifact Registry Maven Setup](https://cloud.google.com/artifact-registry/docs/java/quickstart)

