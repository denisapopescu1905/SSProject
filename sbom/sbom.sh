#!/bin/bash

# Create directories
mkdir -p sbom

# Generate SBOM for Flask backend with Syft
echo "Generating SBOM for Flask backend..."
cd flask_backend || exit
syft dir:. -o cyclonedx-json > ../sbom/flask-backend-sbom.json
cd ..

# Generate Android dependency report
echo "Generating Android dependency report..."
cd android_app || exit

# Check if gradlew exists
if [ -f "./gradlew" ]; then
    echo "Using Gradle wrapper to generate dependency report..."
    chmod +x ./gradlew
    ./gradlew app:dependencies > ../sbom/android-gradle-dependencies.txt
    
    # Generate more detailed dependency graph
    ./gradlew app:androidDependencies > ../sbom/android-detailed-dependencies.txt
    
    # Generate SBOM from build directory which contains resolved dependencies
    if [ -d "./app/build" ]; then
        echo "Scanning Android build directory with Syft..."
        syft dir:./app/build -o cyclonedx-json > ../sbom/android-app-build-sbom.json
    fi
    
    # For more comprehensive scanning of jars
    echo "Scanning Android libs directory with Syft..."
    find . -name "*.jar" -o -name "*.aar" > ../sbom/android-libs-list.txt
    syft dir:. -o cyclonedx-json > ../sbom/android-app-syft-sbom.json
else
    echo "Gradle wrapper not found. Using standard Syft scan..."
    syft dir:. -o cyclonedx-json > ../sbom/android-app-syft-sbom.json
fi

cd ..

# Combine SBOMs or create a merged view
echo "Creating combined project view..."

# Create a combined list of all detected packages
{
    echo "# Combined Software Bill of Materials (SBOM)"
    echo "## Generated on: $(date)"
    echo
    echo "## Flask Backend Dependencies"
    jq -r '.components[] | "- \(.name)@\(.version // "unknown") [\(.type // "unknown")]"' sbom/flask-backend-sbom.json 2>/dev/null | sort
    
    echo
    echo "## Android App Dependencies"
    
    if [ -f "sbom/android-app-syft-sbom.json" ]; then
        echo "### Detected by Syft:"
        jq -r '.components[] | "- \(.name)@\(.version // "unknown") [\(.type // "unknown")]"' sbom/android-app-syft-sbom.json 2>/dev/null | sort
    fi
    
    if [ -f "sbom/android-app-build-sbom.json" ]; then
        echo "### Detected in Build Directory:"
        jq -r '.components[] | "- \(.name)@\(.version // "unknown") [\(.type // "unknown")]"' sbom/android-app-build-sbom.json 2>/dev/null | sort
    fi
    
    if [ -f "sbom/android-gradle-dependencies.txt" ]; then
        echo
        echo "### From Gradle Dependency Report:"
        echo "See complete report in sbom/android-gradle-dependencies.txt"
        echo
        grep -E '\\---' sbom/android-gradle-dependencies.txt | head -20
        echo "... (more in the full report)"
    fi
    
} > sbom/combined-dependencies-report.md

# Generate a full-project SBOM by combining the JSON files
if [ -f "sbom/flask-backend-sbom.json" ] && [ -f "sbom/android-app-syft-sbom.json" ]; then
    echo "Merging SBOMs into unified SBOM..."
    # Extract components from both SBOMs and merge them
    jq -s '.[0].components + .[1].components | unique_by(.name + .version) | {bomFormat: "CycloneDX", specVersion: "1.4", serialNumber: "urn:uuid:'$(uuidgen)'", version: 1, metadata: {timestamp: "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'", tools: [{name: "combined-sbom-script", vendor: "custom"}]}, components: .}' \
        <(jq '.components' sbom/flask-backend-sbom.json) \
        <(jq '.components' sbom/android-app-syft-sbom.json) \
        > sbom/full-project-combined-sbom.json
fi

echo "SBOM generation complete. Results stored in sbom/ directory."
echo "For Android dependencies, please check the Gradle dependency reports in sbom/android-gradle-dependencies.txt"