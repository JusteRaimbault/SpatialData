DB_PORT=5432
DB_USER=juste
DB_NAME=$1

echo "creating database..."
psql -p $DB_PORT -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME';"
psql -p $DB_PORT -d postgres -c "DROP DATABASE $DB_NAME;"
psql -p $DB_PORT -d postgres -c "CREATE DATABASE $DB_NAME;"

echo "loading osm schemas..."
psql -p $DB_PORT -d $DB_NAME -c 'CREATE EXTENSION postgis; CREATE EXTENSION hstore;'
psql -p $DB_PORT -d $DB_NAME -f pgsnapshot_schema_0.6.sql
psql -p $DB_PORT -d $DB_NAME -f pgsnapshot_schema_0.6_linestring.sql


