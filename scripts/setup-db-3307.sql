-- ==============================================================================
-- KEC VAC Database Host (Student 2 Role) - Initialization Script for Port 3307
-- Configures dedicated user vacuser, disables remote root, and flushes grants
-- ==============================================================================

-- 1. Create dedicated database user vacuser allowed from any host (%)
CREATE USER IF NOT EXISTS 'vacuser'@'%' IDENTIFIED BY 'VacSecurePass@2026';
GRANT ALL PRIVILEGES ON kec_vac.* TO 'vacuser'@'%';

-- 2. Restrict root account strictly to localhost (disable remote root login)
DELETE FROM mysql.user WHERE user='root' AND host NOT IN ('localhost', '127.0.0.1', '::1');

-- 3. Apply privilege changes
FLUSH PRIVILEGES;

-- 4. Verification queries
SELECT user, host FROM mysql.user;
SHOW GRANTS FOR 'vacuser'@'%';
