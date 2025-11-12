# Flyway Migration Setup

Flyway has been configured for the fraud-service to manage database schema changes in a production-ready way.

## What Changed

### 1. Dependencies Added (pom.xml)
- `flyway-core` - Core Flyway migration engine
- `flyway-database-postgresql` - PostgreSQL-specific Flyway support

### 2. Configuration (application.properties)
```properties
# Changed from 'update' to 'validate'
spring.jpa.hibernate.ddl-auto=validate

# Flyway enabled
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

**Key changes:**
- `ddl-auto=validate`: Hibernate now only validates the schema instead of auto-creating tables
- `baseline-on-migrate=true`: Allows Flyway to work with existing databases

### 3. Migration Script Created
**Location:** `services/fraud-service/src/main/resources/db/migration/V1__create_fraud_decisions_table.sql`

**What it does:**
- Creates `fraud_decisions` table with proper schema
- Adds 4 optimized indexes:
  - `idx_fraud_decisions_user_id` - For user queries
  - `idx_fraud_decisions_decision` - For filtering REVIEW/BLOCK
  - `idx_fraud_decisions_evaluated_at` - For time-based sorting
  - `idx_fraud_decisions_user_date` - Composite index for user + date range queries

## How It Works

When fraud-service starts:
1. Flyway checks the `flyway_schema_history` table in PostgreSQL
2. If V1 migration hasn't run, it executes the SQL script
3. Flyway records the migration in the history table
4. Hibernate validates that the entities match the database schema

## Testing

**Start PostgreSQL:**
```bash
docker-compose up -d postgres
```

**Run fraud-service:**
```bash
cd services/fraud-service
mvn spring-boot:run
```

**Check migration was applied:**
```bash
# Connect to PostgreSQL
docker exec -it fp-postgres psql -U postgres -d fraud

# View Flyway history
SELECT * FROM flyway_schema_history;

# Check table was created
\dt fraud_decisions
\d fraud_decisions
```

## Adding Future Migrations

To add new schema changes:

1. Create a new migration file following the naming pattern:
   - `V2__add_risk_category_column.sql`
   - `V3__create_user_profiles_table.sql`

2. Version number must increment (V1, V2, V3...)
3. Double underscore `__` between version and description
4. Flyway will automatically run new migrations on startup

## Benefits Over JPA Auto-DDL

✅ **Version Control** - Schema changes are tracked in Git
✅ **Reproducible** - Same migrations run in dev, staging, prod
✅ **Audit Trail** - flyway_schema_history table shows who/when
✅ **Safe** - No accidental schema changes from code modifications
✅ **Team-Friendly** - Conflicts are visible in migration files
✅ **Production-Ready** - Industry standard for database migrations