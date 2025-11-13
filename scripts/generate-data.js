#!/usr/bin/env node

/**
 * Data Generator Script for Fraud Detection System
 * Generates and sends transactions to the ingest API
 * 
 * Usage: node scripts/generate-data.js [count] [delay]
 * Example: node scripts/generate-data.js 100 200
 */

const INGEST_API_URL = process.env.INGEST_API_URL || 'http://localhost:8080';

// Sample data pools
const users = [
  'alice', 'bob', 'charlie', 'diana', 'eve', 'frank', 'grace', 'henry', 
  'ivy', 'jack', 'karen', 'liam', 'mia', 'noah', 'olivia', 'peter', 
  'quinn', 'rachel', 'sam', 'tina', 'user-001', 'user-002', 'user-003'
];
const merchants = [
  'amazon', 'target', 'walmart', 'starbucks', 'mcdonalds', 'apple', 
  'google', 'microsoft', 'netflix', 'spotify', 'uber', 'lyft', 
  'bestbuy', 'homedepot', 'costco', 'nike', 'adidas', 'zara'
];
const currencies = ['USD', 'EUR', 'GBP', 'CAD', 'AUD'];
const cities = [
  { city: 'New York', country: 'US', lat: 40.7128, lon: -74.0060 },
  { city: 'Los Angeles', country: 'US', lat: 34.0522, lon: -118.2437 },
  { city: 'Chicago', country: 'US', lat: 41.8781, lon: -87.6298 },
  { city: 'Houston', country: 'US', lat: 29.7604, lon: -95.3698 },
  { city: 'Phoenix', country: 'US', lat: 33.4484, lon: -112.0740 },
  { city: 'London', country: 'UK', lat: 51.5074, lon: -0.1278 },
  { city: 'Paris', country: 'FR', lat: 48.8566, lon: 2.3522 },
  { city: 'Tokyo', country: 'JP', lat: 35.6762, lon: 139.6503 },
  { city: 'Sydney', country: 'AU', lat: -33.8688, lon: 151.2093 },
  { city: 'Toronto', country: 'CA', lat: 43.6532, lon: -79.3832 }
];
const devices = [
  { id: 'device-001', ip: '192.168.1.100', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' },
  { id: 'device-002', ip: '192.168.1.101', userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36' },
  { id: 'device-003', ip: '192.168.1.102', userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)' },
  { id: 'device-004', ip: '10.0.0.50', userAgent: 'Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/109.0' },
  { id: 'device-005', ip: '172.16.0.10', userAgent: 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)' },
  { id: 'device-006', ip: '203.0.113.5', userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36' },
  { id: 'device-007', ip: '198.51.100.10', userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0)' }
];

function randomElement(array) {
  return array[Math.floor(Math.random() * array.length)];
}

function randomAmount(min = 10, max = 5000) {
  return Math.round((Math.random() * (max - min) + min) * 100) / 100;
}

function generateTransaction(index) {
  const user = randomElement(users);
  const location = randomElement(cities);
  const device = randomElement(devices);
  
  // Generate different transaction patterns
  const pattern = Math.random();
  let amount, isSuspicious = false;
  
  if (pattern < 0.15) {
    // 15% high-value transactions (suspicious)
    amount = randomAmount(2000, 15000);
    isSuspicious = true;
  } else if (pattern < 0.30) {
    // 15% medium-high transactions
    amount = randomAmount(500, 2000);
  } else {
    // 70% normal transactions
    amount = randomAmount(10, 500);
  }
  
  // Add some time variation (spread over last few hours)
  const hoursAgo = Math.random() * 6; // Last 6 hours
  const timestamp = new Date(Date.now() - hoursAgo * 60 * 60 * 1000).toISOString();
  
  return {
    userId: user,
    amount: amount,
    currency: randomElement(currencies),
    merchantId: randomElement(merchants),
    timestamp: timestamp,
    location: {
      lat: location.lat + (Math.random() - 0.5) * 0.1,
      lon: location.lon + (Math.random() - 0.5) * 0.1,
      city: location.city,
      country: location.country
    },
    device: {
      id: device.id,
      ip: device.ip,
      userAgent: device.userAgent
    }
  };
}

async function sendTransaction(tx) {
  try {
    const response = await fetch(`${INGEST_API_URL}/transactions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(tx)
    });

    if (response.status === 202) {
      const txId = response.headers.get('X-Transaction-Id');
      return { success: true, transactionId: txId };
    } else if (response.status === 409) {
      return { success: false, error: 'Duplicate transaction', status: 409 };
    } else {
      const error = await response.text();
      return { success: false, error: error, status: response.status };
    }
  } catch (err) {
    return { success: false, error: err.message };
  }
}

async function generateData(count = 50, delayMs = 200) {
  console.log(`🚀 Generating ${count} transactions...`);
  console.log(`📡 Sending to: ${INGEST_API_URL}`);
  console.log(`⏱️  Delay between requests: ${delayMs}ms\n`);

  let successCount = 0;
  let errorCount = 0;
  let duplicateCount = 0;
  const errors = [];
  const userCounts = new Map();

  for (let i = 0; i < count; i++) {
    const tx = generateTransaction(i);
    const result = await sendTransaction(tx);

    if (result.success) {
      successCount++;
      userCounts.set(tx.userId, (userCounts.get(tx.userId) || 0) + 1);
      process.stdout.write(`✓ ${i + 1}/${count} - ${tx.userId}: ${tx.currency} ${tx.amount.toFixed(2)}                    \r`);
    } else if (result.status === 409) {
      duplicateCount++;
      process.stdout.write(`⚠ ${i + 1}/${count} - Duplicate (skipped)                    \r`);
    } else {
      errorCount++;
      errors.push({ index: i + 1, error: result.error, status: result.status });
      process.stdout.write(`✗ ${i + 1}/${count} - Error: ${result.error.substring(0, 30)}...                    \r`);
    }

    // Delay between requests (except for the last one)
    if (i < count - 1 && delayMs > 0) {
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
  }

  console.log(`\n\n✅ Summary:`);
  console.log(`   Successful: ${successCount}/${count}`);
  console.log(`   Duplicates: ${duplicateCount}`);
  console.log(`   Errors: ${errorCount}/${count}`);
  console.log(`   Unique users: ${userCounts.size}`);
  
  if (userCounts.size > 0) {
    console.log(`\n📊 Top users by transaction count:`);
    Array.from(userCounts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .forEach(([user, count]) => {
        console.log(`   ${user}: ${count} transactions`);
      });
  }

  if (errors.length > 0 && errors.length <= 10) {
    console.log(`\n❌ Errors:`);
    errors.forEach(e => {
      console.log(`   Transaction ${e.index}: ${e.error} (Status: ${e.status || 'N/A'})`);
    });
  }

  console.log(`\n✨ Data generation complete! Check your dashboard to see the new transactions.`);
  console.log(`   Dashboard: http://localhost:5173`);
}

// Parse command line arguments
const count = parseInt(process.argv[2]) || 50;
const delay = parseInt(process.argv[3]) || 200;

// Run the generator
generateData(count, delay).catch(err => {
  console.error('❌ Fatal error:', err);
  process.exit(1);
});

