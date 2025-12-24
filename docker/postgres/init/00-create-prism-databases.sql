-- Create per-service databases for local development.
-- Note: docker-entrypoint-initdb.d scripts run only when the data directory is empty.

SELECT 'CREATE DATABASE \"prism-auth\"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'prism-auth')\gexec

SELECT 'CREATE DATABASE \"prism-core\"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'prism-core')\gexec

SELECT 'CREATE DATABASE \"prism-device\"'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'prism-device')\gexec
