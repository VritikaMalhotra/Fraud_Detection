#!/usr/bin/env python3

"""
Data Generator Script for Fraud Detection System
Generates and sends transactions to the ingest API

Usage: python3 scripts/generate-data.py [count] [delay_ms]
Example: python3 scripts/generate-data.py 100 200
"""

import sys
import json
import random
import time
import urllib.request
import urllib.error
from datetime import datetime, timedelta
from typing import Dict, List

INGEST_API_URL = "http://localhost:8080"

# Sample data pools
USERS = [
    'alice', 'bob', 'charlie', 'diana', 'eve', 'frank', 'grace', 'henry',
    'ivy', 'jack', 'karen', 'liam', 'mia', 'noah', 'olivia', 'peter',
    'quinn', 'rachel', 'sam', 'tina', 'user-001', 'user-002', 'user-003'
]

MERCHANTS = [
    'amazon', 'target', 'walmart', 'starbucks', 'mcdonalds', 'apple',
    'google', 'microsoft', 'netflix', 'spotify', 'uber', 'lyft',
    'bestbuy', 'homedepot', 'costco', 'nike', 'adidas', 'zara'
]

CURRENCIES = ['USD', 'EUR', 'GBP', 'CAD', 'AUD']

CITIES = [
    {'city': 'New York', 'country': 'US', 'lat': 40.7128, 'lon': -74.0060},
    {'city': 'Los Angeles', 'country': 'US', 'lat': 34.0522, 'lon': -118.2437},
    {'city': 'Chicago', 'country': 'US', 'lat': 41.8781, 'lon': -87.6298},
    {'city': 'Houston', 'country': 'US', 'lat': 29.7604, 'lon': -95.3698},
    {'city': 'Phoenix', 'country': 'US', 'lat': 33.4484, 'lon': -112.0740},
    {'city': 'London', 'country': 'UK', 'lat': 51.5074, 'lon': -0.1278},
    {'city': 'Paris', 'country': 'FR', 'lat': 48.8566, 'lon': 2.3522},
    {'city': 'Tokyo', 'country': 'JP', 'lat': 35.6762, 'lon': 139.6503},
    {'city': 'Sydney', 'country': 'AU', 'lat': -33.8688, 'lon': 151.2093},
    {'city': 'Toronto', 'country': 'CA', 'lat': 43.6532, 'lon': -79.3832}
]

DEVICES = [
    {'id': 'device-001', 'ip': '192.168.1.100', 'userAgent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'},
    {'id': 'device-002', 'ip': '192.168.1.101', 'userAgent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)'},
    {'id': 'device-003', 'ip': '192.168.1.102', 'userAgent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)'},
    {'id': 'device-004', 'ip': '10.0.0.50', 'userAgent': 'Mozilla/5.0 (Android 13; Mobile)'},
    {'id': 'device-005', 'ip': '172.16.0.10', 'userAgent': 'Mozilla/5.0 (iPad; CPU OS 17_0)'},
    {'id': 'device-006', 'ip': '203.0.113.5', 'userAgent': 'Mozilla/5.0 (X11; Linux x86_64)'},
    {'id': 'device-007', 'ip': '198.51.100.10', 'userAgent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0)'}
]


# Track user patterns for generating ALLOW transactions
user_devices = {}  # user -> device
user_locations = {}  # user -> location
user_ips = {}  # user -> ip

def generate_transaction(transaction_type: str = 'random') -> Dict:
    """
    Generate a transaction
    
    Args:
        transaction_type: 'allow', 'review', 'block', or 'random'
    """
    user = random.choice(USERS)
    
    # For ALLOW transactions: reuse same device/IP/location for consistency
    # For BLOCK/REVIEW: use new devices/IPs and high amounts
    pattern = random.random()
    
    if transaction_type == 'allow' or (transaction_type == 'random' and pattern < 0.5):
        # ALLOW transactions: low-risk, normal patterns
        # - Very low amount (< 200) to avoid any amount-based scoring
        # - Same device/IP for user (reuse to build history)
        # - Same location (not geo-impossible)
        # - Daytime (6 AM - 11 PM)
        # - Very spaced out (avoid burst)
        amount = round(random.uniform(10, 180), 2)  # Very low to ensure < 1000
        
        # Reuse device/IP for this user (makes it "known" after first use)
        if user not in user_devices:
            user_devices[user] = random.choice(DEVICES)
        device = user_devices[user]
        
        if user not in user_ips:
            user_ips[user] = device['ip']
        
        # Reuse location for this user (same city)
        if user not in user_locations:
            user_locations[user] = random.choice(CITIES)
        location = user_locations[user]
        
        # Daytime hours (6 AM - 11 PM UTC)
        hours_ago = random.uniform(0, 6)
        base_time = datetime.utcnow() - timedelta(hours=hours_ago)
        # Ensure it's between 6 AM and 11 PM
        hour = base_time.hour
        if hour < 6:
            base_time = base_time.replace(hour=6, minute=0, second=0)
        elif hour >= 23:
            base_time = base_time.replace(hour=22, minute=0, second=0)
        timestamp = base_time.isoformat() + 'Z'
        
    elif transaction_type == 'review' or (transaction_type == 'random' and pattern < 0.8):
        # REVIEW transactions: medium-risk
        # - Medium amount (500-1000)
        # - Or new device/IP
        # - Or night time
        amount = round(random.uniform(500, 1000), 2)
        device = random.choice(DEVICES)
        
        # Sometimes use new device/IP
        if random.random() < 0.5:
            user_devices[user] = device  # Mark as new
            user_ips[user] = device['ip']
        
        location = random.choice(CITIES)
        
        # Sometimes night time
        hours_ago = random.uniform(0, 6)
        base_time = datetime.utcnow() - timedelta(hours=hours_ago)
        if random.random() < 0.3:  # 30% chance of night time
            hour = random.randint(0, 5)  # 0-5 AM
            base_time = base_time.replace(hour=hour, minute=0, second=0)
        timestamp = base_time.isoformat() + 'Z'
        
    else:
        # BLOCK transactions: high-risk
        # - High amount (>= 1000)
        # - New device/IP
        # - Different location (geo-impossible)
        amount = round(random.uniform(1000, 15000), 2)
        device = random.choice(DEVICES)
        user_devices[user] = device  # New device
        user_ips[user] = device['ip']
        
        # Different location (far from user's usual)
        if user in user_locations:
            # Pick a far away city
            usual_city = user_locations[user]
            far_cities = [c for c in CITIES if c['city'] != usual_city['city']]
            location = random.choice(far_cities) if far_cities else random.choice(CITIES)
        else:
            location = random.choice(CITIES)
            user_locations[user] = location
        
        hours_ago = random.uniform(0, 1)  # Recent
        timestamp = (datetime.utcnow() - timedelta(hours=hours_ago)).isoformat() + 'Z'
    
    return {
        'userId': user,
        'amount': amount,
        'currency': random.choice(CURRENCIES),
        'merchantId': random.choice(MERCHANTS),
        'timestamp': timestamp,
        'location': {
            'lat': location['lat'] + random.uniform(-0.05, 0.05),
            'lon': location['lon'] + random.uniform(-0.05, 0.05),
            'city': location['city'],
            'country': location['country']
        },
        'device': {
            'id': device['id'],
            'ip': device['ip'],
            'userAgent': device['userAgent']
        }
    }


def send_transaction(tx: Dict) -> Dict:
    """Send transaction to ingest API"""
    try:
        data = json.dumps(tx).encode('utf-8')
        req = urllib.request.Request(
            f"{INGEST_API_URL}/transactions",
            data=data,
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        
        with urllib.request.urlopen(req, timeout=5) as response:
            status_code = response.getcode()
            if status_code == 202:
                tx_id = response.headers.get('X-Transaction-Id', 'N/A')
                return {'success': True, 'transactionId': tx_id}
            else:
                body = response.read().decode('utf-8')
                return {'success': False, 'error': body, 'status': status_code}
    except urllib.error.HTTPError as e:
        if e.code == 409:
            return {'success': False, 'error': 'Duplicate transaction', 'status': 409}
        else:
            body = e.read().decode('utf-8') if e.fp else str(e)
            return {'success': False, 'error': body, 'status': e.code}
    except Exception as e:
        return {'success': False, 'error': str(e)}


def main():
    # Parse arguments: [count] [delay_ms] [type]
    count = 50
    delay_ms = 200
    tx_type = 'random'
    
    if len(sys.argv) > 1:
        try:
            count = int(sys.argv[1])
        except ValueError:
            # If first arg is not a number, it might be the type
            tx_type = sys.argv[1]
    
    if len(sys.argv) > 2:
        try:
            delay_ms = int(sys.argv[2])
        except ValueError:
            # If second arg is not a number, it might be the type
            if tx_type == 'random':
                tx_type = sys.argv[2]
    
    if len(sys.argv) > 3:
        tx_type = sys.argv[3]
    
    print(f"🚀 Generating {count} transactions...")
    print(f"📡 Sending to: {INGEST_API_URL}")
    print(f"⏱️  Delay between requests: {delay_ms}ms")
    if tx_type != 'random':
        print(f"🎯 Transaction type: {tx_type.upper()}")
    print()
    
    success_count = 0
    error_count = 0
    duplicate_count = 0
    errors = []
    user_counts = {}
    
    # For random mode, generate a mix: 50% ALLOW, 30% REVIEW, 20% BLOCK
    for i in range(count):
        if tx_type == 'random':
            # Generate mix of transaction types
            rand = random.random()
            if rand < 0.5:
                current_type = 'allow'
            elif rand < 0.8:
                current_type = 'review'
            else:
                current_type = 'block'
        else:
            current_type = tx_type
        
        tx = generate_transaction(current_type)
        result = send_transaction(tx)
        
        # Add delay to avoid burst detection for ALLOW transactions
        if current_type == 'allow' and delay_ms < 500:
            time.sleep(0.5)  # At least 500ms between ALLOW transactions
        
        if result['success']:
            success_count += 1
            user_counts[tx['userId']] = user_counts.get(tx['userId'], 0) + 1
            print(f"\r✓ {i+1}/{count} - {tx['userId']}: {tx['currency']} {tx['amount']:.2f}", end='', flush=True)
        elif result.get('status') == 409:
            duplicate_count += 1
            print(f"\r⚠ {i+1}/{count} - Duplicate (skipped)", end='', flush=True)
        else:
            error_count += 1
            errors.append({'index': i+1, 'error': result.get('error', 'Unknown'), 'status': result.get('status')})
            print(f"\r✗ {i+1}/{count} - Error: {result.get('error', 'Unknown')[:30]}...", end='', flush=True)
        
        # Delay between requests
        if i < count - 1 and delay_ms > 0:
            time.sleep(delay_ms / 1000.0)
    
    print("\n\n✅ Summary:")
    print(f"   Successful: {success_count}/{count}")
    print(f"   Duplicates: {duplicate_count}")
    print(f"   Errors: {error_count}/{count}")
    print(f"   Unique users: {len(user_counts)}")
    
    if user_counts:
        print("\n📊 Top users by transaction count:")
        sorted_users = sorted(user_counts.items(), key=lambda x: x[1], reverse=True)
        for user, count in sorted_users[:10]:
            print(f"   {user}: {count} transactions")
    
    if errors and len(errors) <= 10:
        print("\n❌ Errors:")
        for e in errors:
            print(f"   Transaction {e['index']}: {e['error']} (Status: {e.get('status', 'N/A')})")
    
    print("\n✨ Data generation complete! Check your dashboard to see the new transactions.")
    print("   Dashboard: http://localhost:5173")


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Fatal error: {e}")
        sys.exit(1)

