# KEC VAC & Skill Certification Engine

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven%203.8+-blue.svg)](https://maven.apache.org/)
[![Database](https://img.shields.io/badge/Database-MySQL%208.0-blue.svg)](https://www.mysql.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Distributed%20Two--Host%20Simulation-green.svg)]()

> A robust, high-performance, console-based enterprise grading and digital certification engine for Kongu Engineering College (KEC) Value-Added Courses (VAC). Engineered for distributed deployment with real remote JDBC connectivity over physical network sockets, non-root user security, and Windows Defender Firewall packet filtering.

---

## 1. System Architecture: Distributed Two-Host Simulation

Due to Oracle Cloud Infrastructure (OCI) requiring a valid international credit card for account verification, this project provides a **Local Two-Host Network Simulation** using a **dedicated standalone MySQL 8 instance on TCP port 3307** that achieves **100% technical and architectural equivalence** to the assignment's two-VM specification without requiring Docker:

```
+------------------------------------+                +------------------------------------+
|   Host 1: Application Node (S1)    |                |    Host 2: Database Node (S2)      |
|    (Java 17, Maven, Thread Pools)  |                |   (Standalone MySQL 8 on Port 3307)|
|                                    |                |                                    |
|  +------------------------------+  |  TCP Port 3307 |  +------------------------------+  |
|  | config/db-remote.properties  |  |  (Restricted)  |  | Windows Defender Firewall    |  |
|  | Host: 192.168.29.137:3307    |  |===============>|  | Inbound Rule: Port 3307 TCP  |  |
|  | User: vacuser                |  |  Physical LAN  |  +--------------+---------------+  |
|  +--------------+---------------+  |  Socket Stream |                 |                  |
|                 |                  |                |  +--------------v---------------+  |
|  +--------------v---------------+  |                |  | Standalone MySQL 8 Daemon    |  |
|  | GradingWorker (Workers 1..4) |  |                |  | Port: 3307 | Bind: 0.0.0.0   |  |
|  | CertWriter (Async 1..3)      |  |                |  | Config: config/my-3307.ini   |  |
|  +------------------------------+  |                |  | Datadir: E:/mysql-remote-data|  |
|                                    |                |  | - Dedicated User: vacuser@%  |  |
|                                    |                |  | - Remote root login: BLOCKED |  |
|                                    |                |  +------------------------------+  |
+------------------------------------+                +------------------------------------+
```

Detailed architectural proof, OCI mapping rationale, and screenshot templates are documented in [docs/ARCHITECTURE_VERIFICATION.md](file:///E:/kec-vac-engine/docs/ARCHITECTURE_VERIFICATION.md).

---

## 2. Directory Structure

```text
E:\kec-vac-engine\
├── .env                                  # Remote database user credentials (vacuser)
├── pom.xml                               # Maven project configuration (Java 17, mysql-connector-j)
├── run.bat                               # Standalone Windows compilation and execution runner
├── config\
│   ├── db.properties                     # Local connection (localhost:3306, user: root)
│   ├── db-remote.properties              # Remote connection (192.168.29.137:3307, user: vacuser)
│   └── my-3307.ini                       # Standalone MySQL 8 config on port 3307 (isolated from 3306)
├── scripts\
│   ├── start-db-3307.bat                 # Starts standalone MySQL 8 on port 3307 (auto-initialises if needed)
│   ├── stop-db-3307.bat                  # Cleanly shuts down MySQL 8 on port 3307
│   ├── setup-db-3307.sql                 # Grants for vacuser@% and remote root disabling script
│   ├── add-firewall-rule.ps1             # PowerShell script to create Windows Defender rule for Port 3307
│   ├── remove-firewall-rule.ps1          # PowerShell script to tear down firewall rule
│   ├── find-lan-ip.bat                   # Interactive LAN IP discovery and remote probe helper
│   ├── test-remote-client.bat            # Windows client remote reachability probe
│   └── test-remote-client.sh             # Linux / Mac / Termux client remote probe
├── docs\
│   └── ARCHITECTURE_VERIFICATION.md      # Full architecture verification report & screenshot guide
├── sql\
│   └── kec_vac_schema.sql                # Complete DDL, CHECK constraints, and comprehensive seed data
├── data\
│   └── quiz_marks.txt                    # Raw quiz stream (valid marks & dirty malformed test lines)
├── lib\
│   └── mysql-connector-j-8.0.33.jar      # Standalone JDBC Driver library
├── logs\
│   ├── execution_proofs.log              # Local batch execution proofs (port 3306)
│   └── execution_proofs_remote.log       # Remote LAN batch execution proofs (port 3307)
├── output\
│   └── certificates\                     # Asynchronously generated digital .txt certificates
├── src\main\java\
│   ├── DBConnection.java                 # Dynamic JDBC manager with -Ddb.config and --remote support
│   ├── VacDAO.java                       # PreparedStatement-only data access layer
│   ├── GradingWorker.java                # Multi-threaded Callable grading engine
│   ├── CertificateWriter.java            # Async BufferedWriter certificate issuer
│   └── MainApp.java                      # Interactive menu, regex validator & full demo runner
└── README.md                             # Comprehensive documentation & setup guide
```

---

## 3. Quick Start: Local Two-Host Distributed Execution

### Step 1: Start Database Host (Student 2 Role)
Run the dedicated batch script:
```cmd
cd E:\kec-vac-engine
scripts\start-db-3307.bat
```
*Starts a standalone MySQL 8 server listening on `0.0.0.0:3307` using `config\my-3307.ini` and storage `E:\mysql-remote-data`.*

To stop it at any time:
```cmd
scripts\stop-db-3307.bat
```

### Step 2: Open Inbound Firewall Port 3307 (Administrator)
Open an elevated PowerShell prompt:
```powershell
powershell -ExecutionPolicy Bypass -File E:\kec-vac-engine\scripts\add-firewall-rule.ps1 -RemoteAddress "Any"
```
To verify the active rule:
```powershell
Get-NetFirewallRule -DisplayName "MySQL Database Remote Host (Port 3307)" | Get-NetFirewallPortFilter
```

### Step 3: Discover LAN IP & Test from Another Device
Run on the host laptop:
```cmd
scripts\find-lan-ip.bat
```
Output will display the host's LAN IP (`192.168.29.137`).

From a friend's laptop or mobile phone connected to the same Wi-Fi / Hotspot:
- **PowerShell (Windows)**:
  ```powershell
  Test-NetConnection -ComputerName 192.168.29.137 -Port 3307
  ```
- **Terminal (Linux / Mac / Android Termux)**:
  ```bash
  nc -zv 192.168.29.137 3307
  ```
- **MySQL CLI (Any device)**:
  ```bash
  mysql -h 192.168.29.137 -P 3307 -u vacuser -p"VacSecurePass@2026" -D kec_vac -e "SHOW TABLES; SELECT @@version, USER();"
  ```

### Step 4: Run Application in Remote Mode (Student 1 Role)
Execute Option 1 (Remote JDBC Self-Test):
```cmd
run.bat --remote
```
Or execute Option 8 (Full Automated End-to-End Demo over Remote Connection):
```cmd
run.bat --remote --demo
```
*All remote execution proofs are logged to `logs/execution_proofs_remote.log`.*

---

## 4. Grading Rules & Business Logic

### Evaluation Tracks
1. **INDUSTRY CERTIFICATION**:
   - Formula: $\text{Total} = (\text{Project} \times 0.50) + (\text{Final Exam} \times 0.30) + (\text{Quiz} \times 0.20)$
   - Constraints: Final Exam $\ge 50\%$; Pass Total $\ge 60\%$.
2. **SKILL DEVELOPMENT**:
   - Formula: $\text{Total} = (\text{Hands-on Labs} \times 0.60) + (\text{Attendance} \times 0.20) + (\text{Project} \times 0.20)$
   - Constraints: Labs $\ge 50\%$; Pass Total $\ge 55\%$.
3. **SOFT SKILLS WORKSHOP**:
   - Formula: $\text{Total} = (\text{Continuous Assessment} \times 0.70) + (\text{Presentation} \times 0.30)$
   - Constraints: Presentation $\ge 50\%$; Pass Total $\ge 50\%$.

### Evaluation Hierarchy (Strict Precedence)
1. **Missing Phased Data**: Marked **`PENDING`**, badge **`PENDING`**, skipped in batch evaluation with reason logged.
2. **Attendance $< 80\%$**: Marked **`INCOMPLETE`**, badge **`INCOMPLETE`**, certificate withheld.
3. **Key-Component Minimum $< 50\%$**: Marked **`FAILED`**, badge **`FAILED`** (prevents passing without exam/lab competence).
4. **Weighted Total $<$ Pass Mark**: Marked **`FAILED`**, badge **`FAILED`**.
5. **Successful Badge Tiering**:
   - Score $\ge 90.0\%$: **`GOLD`**
   - Score $75.0\% - 89.99\%$: **`SILVER`**
   - Score Pass Total to $74.99\%$: **`COMPLETED`**

### VAC Achievement Index Formula
$$\text{VAC Achievement Index} = \frac{\sum (\text{Course Credits} \times \text{Badge Points})}{\text{Total Enrolled VAC Courses}}$$

**Badge Points Constants**:
- `GOLD` = **10 points**
- `SILVER` = **8 points**
- `COMPLETED` = **6 points**
- `FAILED` = **0 points**
- `INCOMPLETE` = **0 points**
- `PENDING` = **0 points**

---

## 5. Verification Proofs & Screenshots Guide

Capture the following 6 screenshots for your Architecture Verification submission (refer to [docs/ARCHITECTURE_VERIFICATION.md](file:///E:/kec-vac-engine/docs/ARCHITECTURE_VERIFICATION.md) for templates):

1. **Screenshot 1: Dedicated MySQL 8 Instance Listening on Port 3307** (`netstat -ano | findstr 3307`).
2. **Screenshot 2: Windows Defender Firewall Inbound Rule Proof** (`Get-NetFirewallRule -DisplayName "MySQL Database Remote Host (Port 3307)" | Get-NetFirewallPortFilter`).
3. **Screenshot 3: Dedicated User `vacuser` Grants on Port 3307** (`SHOW GRANTS FOR 'vacuser'@'%';`).
4. **Screenshot 4: Remote JDBC Connection Proof Output** (`java -cp "bin;lib/mysql-connector-j-8.0.33.jar" DBConnection --remote`).
5. **Screenshot 5: Full Automated Demo Output** (`java -cp "bin;lib/mysql-connector-j-8.0.33.jar" MainApp --remote --demo`).
6. **Screenshot 6: Populated `certificates` Table in Database Host** (`SELECT * FROM certificates;`).

---

## 6. Assumptions Made

1. **Dedicated Standalone MySQL 8 Instance**: A native Windows MySQL 8 server instance running on TCP port `3307` is utilized (configured via `config/my-3307.ini` and storage `E:\mysql-remote-data`) to completely isolate the remote database from any local MySQL service on port `3306`, with zero dependency on Docker.
2. **Physical LAN IP Socket**: A physical Wi-Fi/LAN IPv4 connection (`192.168.29.137:3307`) is utilized in place of OCI VMs to eliminate cloud credit card restrictions while retaining strict client-server network socket isolation.
3. **User Privileges**: Remote root login is disabled; all remote queries execute as `vacuser` with rights restricted strictly to `kec_vac.*`.
4. **Phased Entry Status**: Students with missing required phases receive status `PENDING` and badge `PENDING`. `INCOMPLETE` is strictly reserved for attendance $< 80.0\%$.
5. **Badge Points**: `GOLD = 10`, `SILVER = 8`, `COMPLETED = 6`, `FAILED = 0`, `INCOMPLETE = 0`, `PENDING = 0`.
6. **Thread Naming**: Worker pool counters reset per batch run (`GradingPool-Worker-1..N` and `CertWriter-1..N`).
