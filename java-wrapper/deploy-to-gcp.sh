#!/bin/bash

# GCP Artifact Registry Deployment Script
# This script automates the deployment of Maven artifacts to GCP Artifact Registry

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration (update these values)
PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REPO_NAME="${GCP_REPO_NAME:-maven-public}"
LOCATION="${GCP_LOCATION:-us-central1}"

# Functions
print_header() {
    echo -e "${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "  $1"
}

check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check gcloud CLI
    if ! command -v gcloud &> /dev/null; then
        print_error "gcloud CLI not found. Please install it from: https://cloud.google.com/sdk/docs/install"
        exit 1
    fi
    print_success "gcloud CLI found"
    
    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven not found. Please install Maven 3.6 or later"
        exit 1
    fi
    print_success "Maven found: $(mvn --version | head -n 1)"
    
    # Check authentication
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &> /dev/null; then
        print_error "Not authenticated with gcloud. Run: gcloud auth login"
        exit 1
    fi
    print_success "Authenticated with gcloud: $(gcloud auth list --filter=status:ACTIVE --format='value(account)')"
}

check_project_id() {
    if [ "$PROJECT_ID" = "your-gcp-project-id" ]; then
        print_error "PROJECT_ID not set!"
        echo
        echo "Please set your GCP project ID:"
        echo "  export GCP_PROJECT_ID='your-actual-project-id'"
        echo "  ./deploy-to-gcp.sh"
        echo
        echo "Or edit this script and update the PROJECT_ID variable"
        exit 1
    fi
}

setup_repository() {
    print_header "Setting Up GCP Artifact Registry"
    
    # Set project
    print_info "Setting project to: $PROJECT_ID"
    gcloud config set project "$PROJECT_ID" --quiet
    
    # Check if repository exists
    if gcloud artifacts repositories describe "$REPO_NAME" --location="$LOCATION" &>/dev/null; then
        print_success "Repository '$REPO_NAME' already exists"
    else
        print_info "Creating repository '$REPO_NAME'..."
        gcloud artifacts repositories create "$REPO_NAME" \
            --repository-format=maven \
            --location="$LOCATION" \
            --description="Public Maven repository for claude-code-cf-wrapper"
        print_success "Repository created"
    fi
    
    # Enable public access
    print_info "Configuring public read access..."
    gcloud artifacts repositories add-iam-policy-binding "$REPO_NAME" \
        --location="$LOCATION" \
        --member=allUsers \
        --role=roles/artifactregistry.reader \
        --quiet || true  # Ignore error if already set
    print_success "Public access configured"
}

update_pom_config() {
    print_header "Checking POM Configuration"
    
    local pom_file="pom.xml"
    local repo_url="artifactregistry://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
    
    if [ ! -f "$pom_file" ]; then
        print_error "pom.xml not found in current directory"
        exit 1
    fi
    
    # Check if distributionManagement is configured
    if grep -q "YOUR_PROJECT_ID\|YOUR_REPOSITORY_NAME" "$pom_file"; then
        print_warning "POM contains placeholder values"
        print_info "Updating pom.xml with actual values..."
        
        # Backup original
        cp "$pom_file" "${pom_file}.backup"
        
        # Update placeholders
        sed -i.tmp "s|YOUR_PROJECT_ID|${PROJECT_ID}|g" "$pom_file"
        sed -i.tmp "s|YOUR_REPOSITORY_NAME|${REPO_NAME}|g" "$pom_file"
        rm -f "${pom_file}.tmp"
        
        print_success "POM updated (backup saved as pom.xml.backup)"
    else
        print_success "POM configuration looks good"
    fi
    
    print_info "Repository URL: $repo_url"
}

build_and_deploy() {
    print_header "Building and Deploying"
    
    print_info "Running: mvn clean deploy"
    echo
    
    if mvn clean deploy; then
        echo
        print_success "Deployment successful!"
    else
        echo
        print_error "Deployment failed"
        exit 1
    fi
}

verify_deployment() {
    print_header "Verifying Deployment"
    
    print_info "Listing packages in repository..."
    gcloud artifacts packages list \
        --repository="$REPO_NAME" \
        --location="$LOCATION" \
        --project="$PROJECT_ID"
    
    echo
    print_success "Verification complete"
}

print_next_steps() {
    print_header "Deployment Complete"
    
    echo
    echo "Your artifacts are now publicly available!"
    echo
    echo "📦 GCP Console:"
    echo "   https://console.cloud.google.com/artifacts/maven/${LOCATION}/${REPO_NAME}?project=${PROJECT_ID}"
    echo
    echo "🔗 Public Repository URL (for consumers):"
    echo "   https://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
    echo
    echo "📋 To use in other projects, add to pom.xml:"
    echo
    echo "<repositories>"
    echo "  <repository>"
    echo "    <id>gcp-maven-public</id>"
    echo "    <url>https://${LOCATION}-maven.pkg.dev/${PROJECT_ID}/${REPO_NAME}</url>"
    echo "  </repository>"
    echo "</repositories>"
    echo
    echo "<dependencies>"
    echo "  <dependency>"
    echo "    <groupId>org.tanzu.claudecode</groupId>"
    echo "    <artifactId>claude-code-cf-wrapper</artifactId>"
    echo "    <version>1.0.0</version>"
    echo "  </dependency>"
    echo "</dependencies>"
    echo
}

# Main execution
main() {
    echo
    print_header "GCP Artifact Registry Maven Deployment"
    echo "Project: $PROJECT_ID"
    echo "Repository: $REPO_NAME"
    echo "Location: $LOCATION"
    echo
    
    check_project_id
    check_prerequisites
    setup_repository
    update_pom_config
    build_and_deploy
    verify_deployment
    print_next_steps
}

# Run main function
main "$@"

