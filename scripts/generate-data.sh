#!/bin/bash

# Data Generator Script using curl
# Generates and sends transactions to the ingest API
#
# Usage: ./scripts/generate-data.sh [count] [delay_ms]
# Example: ./scripts/generate-data.sh 100 200

INGEST_API_URL="${INGEST_API_URL:-http://localhost:8080}"
COUNT=${1:-50}
DELAY_MS=${2:-200}

# Sample data pools
users=("alice" "bob" "charlie" "diana" "eve" "frank" "grace" "henry" "ivy" "jack" "karen" "liam" "mia" "noah" "olivia" "peter" "quinn" "rachel" "sam" "tina" "user-001" "user-002" "user-003")
merchants=("amazon" "target" "walmart" "starbucks" "mcdonalds" "apple" "google" "microsoft" "netflix" "spotify" "uber" "lyft" "bestbuy" "homedepot" "costco" "nike" "adidas" "zara")
currencies=("USD" "EUR" "GBP" "CAD" "AUD")

# Cities with coordinates
declare -A cities
cities[0]='{"city":"New York","country":"US","lat":40.7128,"lon":-74.0060}'
cities[1]='{"city":"Los Angeles","country":"US","lat":34.0522,"lon":-118.2437}'
cities[2]='{"city":"Chicago","country":"US","lat":41.8781,"lon":-87.6298}'
cities[3]='{"city":"Houston","country":"US","lat":29.7604,"lon":-95.3698}'
cities[4]='{"city":"London","country":"UK","lat":51.5074,"lon":-0.1278}'
cities[5]='{"city":"Paris","country":"FR","lat":48.8566,"lon":2.3522}'
cities[6]='{"city":"Tokyo","country":"JP","lat":35.6762,"lon":139.6503}'
cities[7]='{"city":"Sydney","country":"AU","lat":-33.8688,"lon":151.2093}'

# Devices
declare -A devices
devices[0]='{"id":"device-001","ip":"192.168.1.100","userAgent":"Mozilla/5.0 (Windows NT 10.0)"}'
devices[1]='{"id":"device-002","ip":"192.168.1.101","userAgent":"Mozilla/5.0 (Macintosh)"}'
devices[2]='{"id":"device-003","ip":"192.168.1.102","userAgent":"Mozilla/5.0 (iPhone)"}'
devices[3]='{"id":"device-004","ip":"10.0.0.50","userAgent":"Mozilla/5.0 (Android)"}'
devices[4]='{"id":"device-005","ip":"172.16.0.10","userAgent":"Mozilla/5.0 (iPad)"}'

# Random element from array
random_element() {
    local arr=("$@")
    echo "${arr[$((RANDOM % ${#arr[@]}))]}"
}

# Random amount
random_amount() {
    local min=${1:-10}
    local max=${2:-5000}
    local amount=$(awk "BEGIN {printf \"%.2f\", $min + rand() * ($max - $min)}")
    echo "$amount"
}

# Generate transaction JSON
generate_transaction() {
    local user=$(random_element "${users[@]}")
    local merchant=$(random_element "${merchants[@]}")
    local currency=$(random_element "${currencies[@]}")
    local city_idx=$((RANDOM % ${#cities[@]}))
    local device_idx=$((RANDOM % ${#devices[@]}))
    
    # 20% chance of high-value transaction
    local pattern=$((RANDOM % 100))
    local amount
    if [ $pattern -lt 20 ]; then
        amount=$(random_amount 1000 10000)
    elif [ $pattern -lt 40 ]; then
        amount=$(random_amount 500 2000)
    else
        amount=$(random_amount 10 500)
    fi
    
    # Timestamp (spread over last 6 hours)
    local hours_ago=$(awk "BEGIN {printf \"%.2f\", rand() * 6}")
    local timestamp=$(date -u -v-${hours_ago}H 2>/dev/null || date -u -d "${hours_ago} hours ago" 2>/dev/null || date -u)
    timestamp=$(date -u -d "$timestamp" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    # Get city and device JSON
    local city_json="${cities[$city_idx]}"
    local device_json="${devices[$device_idx]}"
    
    # Add small variation to coordinates
    local lat=$(echo "$city_json" | grep -o '"lat":[0-9.]*' | cut -d: -f2)
    local lon=$(echo "$city_json" | grep -o '"lon":-[0-9.]*' | cut -d: -f2 || echo "$city_json" | grep -o '"lon":[0-9.]*' | cut -d: -f2)
    local lat_var=$(awk "BEGIN {printf \"%.4f\", ($lat + (rand() - 0.5) * 0.1)}")
    local lon_var=$(awk "BEGIN {printf \"%.4f\", ($lon + (rand() - 0.5) * 0.1)}")
    
    # Build location JSON
    local city_name=$(echo "$city_json" | grep -o '"city":"[^"]*"' | cut -d'"' -f4)
    local country_name=$(echo "$city_json" | grep -o '"country":"[^"]*"' | cut -d'"' -f4)
    local location_json="{\"lat\":$lat_var,\"lon\":$lon_var,\"city\":\"$city_name\",\"country\":\"$country_name\"}"
    
    # Build transaction JSON
    cat <<EOF
{
  "userId": "$user",
  "amount": $amount,
  "currency": "$currency",
  "merchantId": "$merchant",
  "timestamp": "$timestamp",
  "location": $location_json,
  "device": $device_json
}
EOF
}

# Send transaction
send_transaction() {
    local tx_json="$1"
    local response=$(curl -s -w "\n%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$tx_json" \
        "$INGEST_API_URL/transactions")
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n-1)
    
    if [ "$http_code" = "202" ]; then
        echo "success"
    elif [ "$http_code" = "409" ]; then
        echo "duplicate"
    else
        echo "error:$http_code"
    fi
}

# Main execution
echo "🚀 Generating $COUNT transactions..."
echo "📡 Sending to: $INGEST_API_URL"
echo "⏱️  Delay between requests: ${DELAY_MS}ms"
echo ""

success_count=0
error_count=0
duplicate_count=0
declare -A user_counts

for ((i=1; i<=COUNT; i++)); do
    tx_json=$(generate_transaction)
    user=$(echo "$tx_json" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)
    amount=$(echo "$tx_json" | grep -o '"amount":[0-9.]*' | cut -d: -f2)
    currency=$(echo "$tx_json" | grep -o '"currency":"[^"]*"' | cut -d'"' -f4)
    
    result=$(send_transaction "$tx_json")
    
    if [ "$result" = "success" ]; then
        ((success_count++))
        ((user_counts[$user]++))
        printf "\r✓ %d/%d - %s: %s %.2f                    " $i $COUNT "$user" "$currency" "$amount"
    elif [ "$result" = "duplicate" ]; then
        ((duplicate_count++))
        printf "\r⚠ %d/%d - Duplicate (skipped)                    " $i $COUNT
    else
        ((error_count++))
        printf "\r✗ %d/%d - Error                            " $i $COUNT
    fi
    
    # Delay between requests
    if [ $i -lt $COUNT ] && [ $DELAY_MS -gt 0 ]; then
        sleep $(awk "BEGIN {printf \"%.3f\", $DELAY_MS / 1000}")
    fi
done

echo ""
echo ""
echo "✅ Summary:"
echo "   Successful: $success_count/$COUNT"
echo "   Duplicates: $duplicate_count"
echo "   Errors: $error_count/$COUNT"
echo "   Unique users: ${#user_counts[@]}"

if [ ${#user_counts[@]} -gt 0 ]; then
    echo ""
    echo "📊 Top users by transaction count:"
    for user in "${!user_counts[@]}"; do
        echo "   $user: ${user_counts[$user]} transactions"
    done | sort -t: -k2 -nr | head -10
fi

echo ""
echo "✨ Data generation complete! Check your dashboard to see the new transactions."
echo "   Dashboard: http://localhost:5173"

