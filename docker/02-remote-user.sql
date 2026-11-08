-- ==============================================================================
-- KEC VAC Database Host Initialization Security Script
-- Executed automatically on first container startup by Docker entrypoint
-- ==============================================================================

-- 1. Ensure vacuser has complete privileges strictly on kec_vac database from any remote host
GRANT ALL PRIVILEGES ON kec_vac.* TO 'vacuser'@'%';

-- 2. Restrict root account to localhost only (disable remote root login)
DELETE FROM mysql.user WHERE user='root' AND host NOT IN ('localhost', '127.0.0.1', '::1');

-- 3. Apply privilege changes
FLUSH PRIVILEGES;

-- 4. Verification queries
SELECT user, host FROM mysql.user;
SHOW GRANTS FOR 'vacuser'@'%';
