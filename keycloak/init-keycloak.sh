#!/bin/bash

# Keycloak initialization script
# This script waits for Keycloak to start and imports the realm configuration

echo "🔐 Initializing Keycloak with GBC_Realm configuration..."

# Wait for Keycloak to be ready
wait_for_keycloak() {
    echo "⏳ Waiting for Keycloak to start..."

    for i in {1..60}; do
        if curl -s -f "http://localhost:8180/auth/realms/master" > /dev/null 2>&1; then
            echo "✅ Keycloak is ready!"
            return 0
        fi
        echo "   Attempt $i/60: Keycloak not ready yet..."
        sleep 5
    done

    echo "❌ Keycloak failed to start within timeout"
    return 1
}

# Get admin access token
get_admin_token() {
    echo "🔑 Getting admin access token..."

    ADMIN_TOKEN=$(curl -s -X POST \
        "http://localhost:8180/auth/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=admin" \
        -d "password=admin" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" | jq -r '.access_token')

    if [ "$ADMIN_TOKEN" != "null" ] && [ "$ADMIN_TOKEN" != "" ]; then
        echo "✅ Admin token obtained"
        return 0
    else
        echo "❌ Failed to get admin token"
        return 1
    fi
}

# Import realm configuration
import_realm() {
    echo "📥 Importing GBC_Realm configuration..."

    curl -s -X POST \
        "http://localhost:8180/auth/admin/realms" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d @gbc-realm.json

    if [ $? -eq 0 ]; then
        echo "✅ Realm imported successfully"
        return 0
    else
        echo "❌ Failed to import realm"
        return 1
    fi
}

# Verify realm import
verify_realm() {
    echo "🔍 Verifying realm import..."

    REALM_CHECK=$(curl -s -X GET \
        "http://localhost:8180/auth/admin/realms/GBC_Realm" \
        -H "Authorization: Bearer $ADMIN_TOKEN")

    if echo "$REALM_CHECK" | jq -e '.realm == "GBC_Realm"' > /dev/null; then
        echo "✅ Realm verification successful"
        return 0
    else
        echo "❌ Realm verification failed"
        return 1
    fi
}

# Main execution
main() {
    if wait_for_keycloak && get_admin_token && import_realm && verify_realm; then
        echo ""
        echo "🎉 Keycloak initialization completed successfully!"
        echo ""
        echo "📋 Keycloak Configuration:"
        echo "   Keycloak Admin Console: http://localhost:8180/auth/admin"
        echo "   Realm: GBC_Realm"
        echo "   Admin User: admin / admin"
        echo ""
        echo "📋 Test Users:"
        echo "   Admin: admin / admin123"
        echo "   Instructor: instructor1 / instructor123"
        echo "   Student: student1 / student123"
        echo "   Student: student2 / student123"
        echo ""
        echo "🔗 Realm Endpoints:"
        echo "   Token Endpoint: http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/token"
        echo "   Auth Endpoint: http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/auth"
        echo "   Userinfo Endpoint: http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/userinfo"
        return 0
    else
        echo "❌ Keycloak initialization failed"
        return 1
    fi
}

# Run the main function
main "$@"