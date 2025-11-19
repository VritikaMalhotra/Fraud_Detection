"""
Test script to verify all Docker services are running correctly
"""
import sys

def test_postgres():
    """Test PostgreSQL connection"""
    try:
        import psycopg2
        conn = psycopg2.connect(
            host="localhost",
            port=5432,
            database="fraud",
            user="postgres",
            password="postgres"
        )
        cursor = conn.cursor()
        cursor.execute("SELECT version();")
        version = cursor.fetchone()
        cursor.close()
        conn.close()
        print("✓ PostgreSQL: Connected successfully")
        print(f"  Version: {version[0][:50]}...")
        return True
    except ImportError:
        print("✗ PostgreSQL: psycopg2 library not installed")
        print("  Install with: pip install psycopg2-binary")
        return False
    except Exception as e:
        print(f"✗ PostgreSQL: Connection failed - {e}")
        return False

def test_redis():
    """Test Redis connection"""
    try:
        import redis
        r = redis.Redis(host='localhost', port=6379, decode_responses=True)
        r.ping()
        r.set('test_key', 'test_value')
        value = r.get('test_key')
        r.delete('test_key')
        print("✓ Redis: Connected successfully")
        print(f"  Test read/write: OK")
        return True
    except ImportError:
        print("✗ Redis: redis library not installed")
        print("  Install with: pip install redis")
        return False
    except Exception as e:
        print(f"✗ Redis: Connection failed - {e}")
        return False

def test_kafka():
    """Test Kafka connection"""
    try:
        from kafka import KafkaProducer, KafkaConsumer, KafkaAdminClient
        from kafka.admin import NewTopic
        import json

        # Test admin connection and list topics
        admin = KafkaAdminClient(bootstrap_servers='localhost:9094')
        topics = admin.list_topics()
        print("✓ Kafka: Connected successfully")
        print(f"  Available topics: {', '.join(topics)}")

        # Test producer
        producer = KafkaProducer(
            bootstrap_servers='localhost:9094',
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        test_message = {'test': 'message', 'timestamp': '2024-01-01'}
        producer.send('payments.events', test_message)
        producer.flush()
        producer.close()
        print("  Test producer: OK")

        admin.close()
        return True
    except ImportError:
        print("✗ Kafka: kafka-python library not installed")
        print("  Install with: pip install kafka-python")
        return False
    except Exception as e:
        print(f"✗ Kafka: Connection failed - {e}")
        return False

def main():
    print("=" * 60)
    print("Testing Fraud Detection Services")
    print("=" * 60)
    print()

    results = {
        'PostgreSQL': test_postgres(),
        'Redis': test_redis(),
        'Kafka': test_kafka()
    }

    print()
    print("=" * 60)
    print("Summary")
    print("=" * 60)

    passed = sum(results.values())
    total = len(results)

    for service, status in results.items():
        status_symbol = "✓" if status else "✗"
        print(f"{status_symbol} {service}")

    print()
    print(f"Total: {passed}/{total} services working")

    if passed == total:
        print("\n🎉 All services are running correctly!")
        sys.exit(0)
    else:
        print("\n⚠️  Some services need attention")
        sys.exit(1)

if __name__ == "__main__":
    main()
