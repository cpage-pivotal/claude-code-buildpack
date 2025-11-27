# Quick Start: Deploy to GCP Artifact Registry

## TL;DR - Quick Commands

```bash
# 1. Set your GCP project ID
export GCP_PROJECT_ID="your-gcp-project-id"

# 2. Run the deployment script
cd java-wrapper
./deploy-to-gcp.sh
```

That's it! The script will:
- ✓ Check prerequisites (gcloud, Maven)
- ✓ Create the Artifact Registry repository
- ✓ Configure public read access
- ✓ Update your pom.xml automatically
- ✓ Build and deploy your artifacts
- ✓ Verify the deployment

## Manual Deployment (Alternative)

If you prefer to do it manually:

### Step 1: Create Repository
```bash
export PROJECT_ID="your-gcp-project-id"
export REPO_NAME="maven-public"
export LOCATION="us-central1"

gcloud config set project $PROJECT_ID

gcloud artifacts repositories create $REPO_NAME \
    --repository-format=maven \
    --location=$LOCATION \
    --description="Public Maven repository"
```

### Step 2: Make Public
```bash
gcloud artifacts repositories add-iam-policy-binding $REPO_NAME \
    --location=$LOCATION \
    --member=allUsers \
    --role=roles/artifactregistry.reader
```

### Step 3: Update pom.xml
Replace placeholders in `distributionManagement` section:
- `YOUR_PROJECT_ID` → your actual project ID
- `YOUR_REPOSITORY_NAME` → `maven-public`

### Step 4: Deploy
```bash
cd java-wrapper
mvn clean deploy
```

## Using the Published Artifacts

Add to your project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>gcp-maven-public</id>
        <url>https://us-central1-maven.pkg.dev/YOUR_PROJECT_ID/maven-public</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.tanzu.claudecode</groupId>
        <artifactId>claude-code-cf-wrapper</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

No authentication required for consumers!

## Troubleshooting

**Not authenticated?**
```bash
gcloud auth login
gcloud auth application-default login
```

**Permission denied?**
```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="user:$(gcloud config get-value account)" \
    --role="roles/artifactregistry.writer"
```

**Need help?**
See full documentation in `GCP_DEPLOYMENT.md`

