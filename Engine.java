package certchain.engine;

import certchain.core.Core.*;
import certchain.ui.Screens.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/** All classes of the "engine" part of CertChain, kept in one file. */
public final class Engine {
    private Engine() {
    }

    // ===================== AccountRepository =====================
    /** Stores login accounts in the "accounts" table. */
    public static class AccountRepository extends JdbcRepository<Account, String> {
        private static final RowMapper<Account> MAPPER = row -> new Account(row.getString("username"),
                Role.valueOf(row.getString("role")), row.getString("salt"), row.getString("pass_hash"),
                row.getString("display_name"), row.getString("issuer_id"), row.getString("created"),
                row.getString("email"), row.getString("phone"));

        public AccountRepository(Database database) {
            super(database);
        }

        @Override
        public void save(Account a) throws SQLException {
            update("INSERT OR REPLACE INTO accounts (username, role, salt, pass_hash, display_name, issuer_id, created, email, phone)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)", a.getUsername(), a.getRole().name(), a.getSalt(),
                    a.getPasswordHash(), a.getDisplayName(), a.getIssuerId(), a.getCreated(), a.getEmail(), a.getPhone());
        }

        @Override
        public Account findById(String username) throws SQLException {
            List<Account> rows = query("SELECT * FROM accounts WHERE username = ?", MAPPER, username);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public List<Account> findAll() throws SQLException {
            return query("SELECT * FROM accounts ORDER BY role, username", MAPPER);
        }

        public void delete(String username) throws SQLException {
            update("DELETE FROM accounts WHERE username = ?", username);
        }
    }

    // ===================== BlockRepository =====================
    /** Stores blocks in the "blocks" table. */
    public static class BlockRepository extends JdbcRepository<Block, Integer> {

        /** Row to Block. The column rtype tells which BlockData subclass to create. */
        private static final RowMapper<Block> MAPPER = row -> {
            String type = row.getString("rtype");
            String docHash = row.getString("doc_hash");
            String issuerId = row.getString("issuer_id");
            String issuerName = row.getString("issuer_name");
            String date = row.getString("date_text");
            BlockData data;
            switch (type) {
                case "GENESIS":
                    data = new GenesisData(date);
                    break;
                case "CERTIFICATE":
                    data = new CertificateData(docHash, issuerId, issuerName, date, row.getString("cert_id"),
                            row.getString("holder"), row.getString("course"), row.getString("grade"),
                            row.getString("year"), row.getString("cert_number"), row.getString("source"));
                    break;
                case "CONFIRMED":
                    data = new IssuerConfirmedCertificate(docHash, issuerId, issuerName, date, row.getString("cert_id"),
                            row.getString("holder"), row.getString("course"), row.getString("grade"),
                            row.getString("year"), row.getString("cert_number"), row.getString("request_id"),
                            row.getString("remarks"));
                    break;
                case "REJECTION":
                    data = new RejectionData(docHash, issuerId, issuerName, date, row.getString("holder"),
                            row.getString("request_id"), row.getString("remarks"));
                    break;
                default:
                    throw new SQLException("Unknown block type: " + type);
            }
            return new Block(row.getInt("idx"), row.getLong("ts"), data, row.getString("prev_hash"),
                    row.getLong("nonce"), row.getString("hash"));
        };

        public BlockRepository(Database database) {
            super(database);
        }

        @Override
        public void save(Block b) throws SQLException {
            BlockData d = b.getData();
            String certId = "", holder = "", course = "", grade = "", year = "", certNumber = "", source = "";
            String requestId = "", remarks = "";
            if (d instanceof CertificateData) {
                CertificateData c = (CertificateData) d;
                certId = c.getCertId();
                holder = c.getHolder();
                course = c.getCourse();
                grade = c.getGrade();
                year = c.getYear();
                certNumber = c.getCertNumber();
                source = c.getSource();
            }
            if (d instanceof IssuerConfirmedCertificate) {
                IssuerConfirmedCertificate c = (IssuerConfirmedCertificate) d;
                requestId = c.getRequestId();
                remarks = c.getRemarks();
            }
            if (d instanceof RejectionData) {
                RejectionData r = (RejectionData) d;
                holder = r.getHolder();
                requestId = r.getRequestId();
                remarks = r.getRemarks();
            }
            update("INSERT INTO blocks VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    b.getIndex(), b.getTimestamp(), d.getType(), d.getDocHash(), d.getIssuerId(), d.getIssuerName(),
                    d.getDate(), certId, holder, course, grade, year, certNumber, source, requestId, remarks,
                    b.getPreviousHash(), b.getNonce(), b.getHash());
        }

        @Override
        public Block findById(Integer index) throws SQLException {
            List<Block> rows = query("SELECT * FROM blocks WHERE idx = ?", MAPPER, index);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public List<Block> findAll() throws SQLException {
            return query("SELECT * FROM blocks ORDER BY idx", MAPPER);
        }

        /** Used only by the tampering demo: edits a stored holder name WITHOUT recomputing the hash. */
        public int overwriteHolder(int index, String newHolder) throws SQLException {
            return update("UPDATE blocks SET holder = ? WHERE idx = ?", newHolder, index);
        }

        public String holderOf(int index) throws SQLException {
            List<String> rows = query("SELECT holder FROM blocks WHERE idx = ?", r -> r.getString(1), index);
            return rows.isEmpty() ? "" : rows.get(0);
        }
    }

    // ===================== Database =====================
    /** Opens the SQLite database through JDBC and creates the tables (Unit 4: JDBC). */
    public static class Database {
        /** All threads share one connection, so every statement runs inside synchronized (LOCK). */
        public static final Object LOCK = new Object();

        private final Connection connection;
        private final File file;

        public Database(File file) throws SQLException {
            this.file = file;
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found. Add sqlite-jdbc-3.x.jar to the classpath (lib folder).", e);
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            createTables();
        }

        private void createTables() throws SQLException {
            synchronized (LOCK) {
                Statement st = connection.createStatement();
                try {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS blocks ("
                            + "idx INTEGER PRIMARY KEY, ts INTEGER, rtype TEXT, doc_hash TEXT, issuer_id TEXT, "
                            + "issuer_name TEXT, date_text TEXT, cert_id TEXT, holder TEXT, course TEXT, grade TEXT, "
                            + "year TEXT, cert_number TEXT, source TEXT, request_id TEXT, remarks TEXT, "
                            + "prev_hash TEXT, nonce INTEGER, hash TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS issuers ("
                            + "id TEXT PRIMARY KEY, name TEXT, key_hash TEXT, created TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                            + "username TEXT PRIMARY KEY, role TEXT, salt TEXT, pass_hash TEXT, display_name TEXT, "
                            + "issuer_id TEXT, created TEXT)");
                    addColumn(st, "accounts", "email");
                    addColumn(st, "accounts", "phone");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS issuer_keys ("
                            + "issuer_id TEXT PRIMARY KEY, public_key TEXT, locked_private TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS signatures ("
                            + "doc_hash TEXT PRIMARY KEY, issuer_id TEXT, signature TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS requests ("
                            + "id TEXT PRIMARY KEY, issuer_id TEXT, company TEXT, candidate TEXT, cert_number TEXT, "
                            + "course TEXT, year TEXT, doc_hash TEXT, file_name TEXT, stored_as TEXT, status TEXT, "
                            + "remarks TEXT, created TEXT, decided_on TEXT, cert_id TEXT)");
                } finally {
                    st.close();
                }
            }
        }

        /** Old databases get the new column once; if it already exists SQLite complains and we ignore that. */
        private static void addColumn(Statement st, String table, String column) {
            try {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT DEFAULT ''");
            } catch (SQLException alreadyThere) {
                // column exists: nothing to do
            }
        }

        public Connection getConnection() {
            return connection;
        }

        public File getFile() {
            return file;
        }

        public void close() {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing at exit: nothing useful to do
            }
        }
    }

    // ===================== IssuerRepository =====================
    /** Stores approved issuers in the "issuers" table. */
    public static class IssuerRepository extends JdbcRepository<Issuer, String> {
        private static final RowMapper<Issuer> MAPPER = row ->
                new Issuer(row.getString("id"), row.getString("name"), row.getString("key_hash"), row.getString("created"));

        public IssuerRepository(Database database) {
            super(database);
        }

        @Override
        public void save(Issuer i) throws SQLException {
            update("INSERT OR REPLACE INTO issuers VALUES (?,?,?,?)", i.getId(), i.getName(), i.getKeyHash(), i.getCreated());
        }

        @Override
        public Issuer findById(String id) throws SQLException {
            List<Issuer> rows = query("SELECT * FROM issuers WHERE id = ?", MAPPER, id);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public List<Issuer> findAll() throws SQLException {
            return query("SELECT * FROM issuers ORDER BY name", MAPPER);
        }
    }

    // ===================== JdbcRepository =====================
    /**
     * Abstract generic class (Unit 5: generic class hierarchy). It contains the JDBC code that all
     * repositories share; the subclasses only supply SQL and the row mapping.
     */
    public static abstract class JdbcRepository<T, ID> implements Repository<T, ID> {
        protected final Connection connection;

        protected JdbcRepository(Database database) {
            this.connection = database.getConnection();
        }

        /** Generic method: runs a SELECT and maps each row to R. */
        protected <R> List<R> query(String sql, RowMapper<R> mapper, Object... params) throws SQLException {
            synchronized (Database.LOCK) {
                PreparedStatement ps = connection.prepareStatement(sql);
                try {
                    bind(ps, params);
                    ResultSet rs = ps.executeQuery();
                    try {
                        List<R> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(mapper.map(rs));
                        }
                        return rows;
                    } finally {
                        rs.close();
                    }
                } finally {
                    ps.close();
                }
            }
        }

        /** INSERT / UPDATE / DELETE. */
        protected int update(String sql, Object... params) throws SQLException {
            synchronized (Database.LOCK) {
                PreparedStatement ps = connection.prepareStatement(sql);
                try {
                    bind(ps, params);
                    return ps.executeUpdate();
                } finally {
                    ps.close();
                }
            }
        }

        private void bind(PreparedStatement ps, Object[] params) throws SQLException {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    // ===================== Repository =====================
    /** Generic interface with two type parameters: T is the object type, ID the type of its key (Unit 5). */
    public interface Repository<T, ID> {
        void save(T item) throws SQLException;

        T findById(ID id) throws SQLException;

        List<T> findAll() throws SQLException;
    }

    // ===================== SignatureStore =====================
    /** Issuer key pairs, certificate signatures and a small meta table (all in SQLite). */
    public static class SignatureStore extends JdbcRepository<String[], String> {
        public SignatureStore(Database database) {
            super(database);
        }

        /** Row = {docHash, issuerId, signature}. */
        @Override
        public void save(String[] row) throws SQLException {
            update("INSERT OR REPLACE INTO signatures VALUES (?,?,?)", row[0], row[1], row[2]);
        }

        @Override
        public String[] findById(String docHash) throws SQLException {
            List<String[]> rows = query("SELECT * FROM signatures WHERE doc_hash = ?",
                    r -> new String[]{r.getString("doc_hash"), r.getString("issuer_id"), r.getString("signature")}, docHash);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public List<String[]> findAll() throws SQLException {
            return query("SELECT * FROM signatures",
                    r -> new String[]{r.getString("doc_hash"), r.getString("issuer_id"), r.getString("signature")});
        }

        /** Key row = {issuerId, publicKey, lockedPrivate}. */
        public String[] findKey(String issuerId) throws SQLException {
            List<String[]> rows = query("SELECT * FROM issuer_keys WHERE issuer_id = ?",
                    r -> new String[]{r.getString("issuer_id"), r.getString("public_key"), r.getString("locked_private")}, issuerId);
            return rows.isEmpty() ? null : rows.get(0);
        }

        public void saveKey(String issuerId, String publicKey, String lockedPrivate) throws SQLException {
            update("INSERT OR REPLACE INTO issuer_keys VALUES (?,?,?)", issuerId, publicKey, lockedPrivate);
        }

        public String meta(String key) throws SQLException {
            List<String> rows = query("SELECT v FROM meta WHERE k = ?", r -> r.getString("v"), key);
            return rows.isEmpty() ? null : rows.get(0);
        }

        public void setMeta(String key, String value) throws SQLException {
            update("INSERT OR REPLACE INTO meta VALUES (?,?)", key, value);
        }
    }

    // ===================== RequestRepository =====================
    /** Stores verification requests in the "requests" table. */
    public static class RequestRepository extends JdbcRepository<VerificationRequest, String> {
        private static final RowMapper<VerificationRequest> MAPPER = row -> new VerificationRequest(
                row.getString("id"), row.getString("issuer_id"), row.getString("company"), row.getString("candidate"),
                row.getString("cert_number"), row.getString("course"), row.getString("year"), row.getString("doc_hash"),
                row.getString("file_name"), row.getString("stored_as"), RequestStatus.valueOf(row.getString("status")),
                row.getString("remarks"), row.getString("created"), row.getString("decided_on"), row.getString("cert_id"));

        public RequestRepository(Database database) {
            super(database);
        }

        @Override
        public void save(VerificationRequest r) throws SQLException {
            update("INSERT OR REPLACE INTO requests VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    r.getId(), r.getIssuerId(), r.getCompany(), r.getCandidate(), r.getCertNumber(), r.getCourse(),
                    r.getYear(), r.getDocHash(), r.getFileName(), r.getStoredAs(), r.getStatus().name(), r.getRemarks(),
                    r.getCreated(), r.getDecidedOn(), r.getCertId());
        }

        @Override
        public VerificationRequest findById(String id) throws SQLException {
            List<VerificationRequest> rows = query("SELECT * FROM requests WHERE id = ?", MAPPER, id);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public List<VerificationRequest> findAll() throws SQLException {
            return query("SELECT * FROM requests ORDER BY created DESC, id", MAPPER);
        }

        public List<VerificationRequest> findByIssuer(String issuerId) throws SQLException {
            return query("SELECT * FROM requests WHERE issuer_id = ? ORDER BY (status = 'PENDING') DESC, id", MAPPER,
                    issuerId);
        }

        public VerificationRequest findPendingByHash(String docHash) throws SQLException {
            List<VerificationRequest> rows =
                    query("SELECT * FROM requests WHERE doc_hash = ? AND status = 'PENDING'", MAPPER, docHash);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    // ===================== RowMapper =====================
    /** Generic interface (Unit 5): turns one database row into an object of type R. */
    public interface RowMapper<R> {
        R map(ResultSet row) throws SQLException;
    }

    // ===================== Blockchain =====================
    /**
     * The chain itself. Several collections are used on purpose (Unit 4):
     *   ArrayList  - the blocks in order
     *   HashMap    - latest block for each document hash
     *   Hashtable  - block for each certificate ID
     *   HashSet    - every certificate ID ever issued
     * Public methods are synchronized because the miner thread, the watchdog thread and the
     * user interface thread all use the chain (Unit 3: synchronizing threads).
     */
    public static class Blockchain {
        public static final int DIFFICULTY = 4;
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

        private final List<Block> chain = new ArrayList<>();
        private final Map<String, Block> byDocHash = new HashMap<>();
        private final Map<String, Block> byCertId = new Hashtable<>();
        private final Set<String> certIds = new HashSet<>();
        private final BlockRepository repository;

        public Blockchain(BlockRepository repository) throws SQLException {
            this.repository = repository;
            reload();
            if (chain.isEmpty()) {
                Block genesis = new Block(0, new GenesisData(LocalDate.now().format(DATE)), "0");
                genesis.mine(DIFFICULTY);
                repository.save(genesis);
                register(genesis);
            }
        }

        /** Reads every block from the database again. */
        public synchronized void reload() throws SQLException {
            chain.clear();
            byDocHash.clear();
            byCertId.clear();
            certIds.clear();
            for (Block b : repository.findAll()) {
                register(b);
            }
        }

        private void register(Block block) {
            chain.add(block);
            BlockData data = block.getData();
            if (!data.getDocHash().isEmpty()) {
                byDocHash.put(data.getDocHash(), block); // later blocks replace earlier ones
            }
            if (data instanceof CertificateData) {
                String id = ((CertificateData) data).getCertId();
                byCertId.put(id, block);
                certIds.add(id);
            }
        }

        /** Step 1 of adding a block: create it, linked to the current last block. */
        public synchronized Block prepareNext(BlockData data) {
            Block last = chain.get(chain.size() - 1);
            return new Block(chain.size(), data, last.getHash());
        }

        /** Step 3: after the block was mined (outside the lock), add and save it. */
        public synchronized void commit(Block block) throws CertChainException, SQLException {
            Block last = chain.get(chain.size() - 1);
            boolean linked = block.getIndex() == chain.size() && block.getPreviousHash().equals(last.getHash());
            boolean mined = block.getHash().startsWith("0".repeat(DIFFICULTY)) && block.hasValidHash();
            if (!linked || !mined) {
                throw new TamperedChainException("Block " + block.getIndex() + " was rejected by the chain", block.getIndex());
            }
            repository.save(block);
            register(block);
        }

        /** Overloaded find: by document hash ... */
        public synchronized Block find(String docHash) {
            return byDocHash.get(docHash);
        }

        /** ... or by block number. */
        public synchronized Block find(int index) {
            return (index >= 0 && index < chain.size()) ? chain.get(index) : null;
        }

        public synchronized Block findByCertId(String certId) {
            return byCertId.get(certId);
        }

        public synchronized boolean hasCertId(String certId) {
            return certIds.contains(certId);
        }

        public synchronized List<Block> snapshot() {
            return new ArrayList<>(chain);
        }

        public synchronized int size() {
            return chain.size();
        }

        /** Walks the chain with an Iterator and checks hashes, links and proof of work. */
        public synchronized ValidationReport validate() {
            String prefix = "0".repeat(DIFFICULTY);
            Iterator<Block> it = chain.iterator();
            Block previous = null;
            while (it.hasNext()) {
                Block b = it.next();
                if (!b.hasValidHash()) {
                    return new ValidationReport(false, "Block " + b.getIndex() + " has been tampered with", b.getIndex());
                }
                if (previous != null) {
                    if (!b.getPreviousHash().equals(previous.getHash())) {
                        return new ValidationReport(false, "Block " + b.getIndex() + " is not linked to block "
                                + previous.getIndex(), b.getIndex());
                    }
                    if (!b.getHash().startsWith(prefix)) {
                        return new ValidationReport(false, "Block " + b.getIndex() + " has no valid proof of work",
                                b.getIndex());
                    }
                }
                previous = b;
            }
            return new ValidationReport(true, "All " + chain.size() + " blocks are valid", -1);
        }

        /** Same check, but reported with an exception (Unit 2: throw / throws). */
        public void requireValid() throws TamperedChainException {
            ValidationReport report = validate();
            if (!report.isValid()) {
                throw new TamperedChainException(report.getMessage(), report.getBadBlockIndex());
            }
        }
    }

    // ===================== ChainWatchdog =====================
    /**
     * Creates a thread by extending the Thread class (Unit 3) and gives it the lowest priority.
     * It re-validates the whole chain every few seconds, so tampering is noticed without anyone clicking.
     */
    public static class ChainWatchdog extends Thread {
        private final Blockchain chain;
        private final ResultHandler<ValidationReport> handler;
        private final long periodMillis;

        public ChainWatchdog(Blockchain chain, long periodMillis, ResultHandler<ValidationReport> handler) {
            super("chain-watchdog");
            this.chain = chain;
            this.periodMillis = periodMillis;
            this.handler = handler;
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                handler.onSuccess(chain.validate());
                try {
                    Thread.sleep(periodMillis);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    // ===================== Miner =====================
    /** Creates threads by implementing Runnable (Unit 3). Mines one queued block after another. */
    public static class Miner implements Runnable {
        private final Blockchain chain;
        private final MiningQueue queue;
        private final ActivityLog log;

        public Miner(Blockchain chain, MiningQueue queue, ActivityLog log) {
            this.chain = chain;
            this.queue = queue;
            this.log = log;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                MiningJob job;
                try {
                    job = queue.take(); // waits here while the queue is empty
                } catch (InterruptedException e) {
                    break; // application is closing
                }
                long start = System.currentTimeMillis();
                try {
                    Block block = chain.prepareNext(job.getData());
                    block.mine(Blockchain.DIFFICULTY); // the slow part, done without holding the lock
                    chain.commit(block);
                    log.add("Block #" + block.getIndex() + " mined in " + (System.currentTimeMillis() - start)
                            + " ms (nonce " + block.getNonce() + ")");
                    job.getHandler().onSuccess(block);
                } catch (Exception e) {
                    log.add("Mining failed: " + e.getMessage());
                    job.getHandler().onError(e);
                }
            }
        }
    }

    // ===================== Mailer =====================
    /**
     * Sends a plain-text e-mail through an SMTP server over SSL (port 465) using only java.net and javax.net.ssl
     * (Unit 3: sockets and byte streams). Settings come from environment variables:
     *   CERT_SMTP_USER (e-mail address), CERT_SMTP_PASS (app password), optional CERT_SMTP_HOST (default smtp.gmail.com).
     * Without CERT_SMTP_USER the app runs in demo mode and shows the OTP on screen instead.
     */
    public static class Mailer {
        private final String host = env("CERT_SMTP_HOST", "smtp.gmail.com");
        private final int port = Integer.parseInt(env("CERT_SMTP_PORT", "465"));
        private final String user = env("CERT_SMTP_USER", "");
        private final String pass = env("CERT_SMTP_PASS", "");
        private final boolean ssl = !"false".equalsIgnoreCase(env("CERT_SMTP_SSL", "true"));

        private static String env(String name, String fallback) {
            String v = System.getenv(name);
            return v == null || v.trim().isEmpty() ? fallback : v.trim();
        }

        public boolean isConfigured() {
            return !user.isEmpty();
        }

        public void send(String to, String subject, String body) throws IOException {
            java.net.Socket socket = ssl ? javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port)
                    : new java.net.Socket(host, port);
            try {
                socket.setSoTimeout(20000);
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                java.io.Writer out = new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
                expect(in, "220");
                command(in, out, "EHLO certchain", "250");
                if (!pass.isEmpty()) {
                    command(in, out, "AUTH LOGIN", "334");
                    command(in, out, java.util.Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)), "334");
                    command(in, out, java.util.Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)), "235");
                }
                command(in, out, "MAIL FROM:<" + user + ">", "250");
                command(in, out, "RCPT TO:<" + to + ">", "250");
                command(in, out, "DATA", "354");
                out.write("From: CertChain <" + user + ">\r\nTo: " + to + "\r\nSubject: " + subject
                        + "\r\nContent-Type: text/plain; charset=US-ASCII\r\n\r\n" + body.replace("\n", "\r\n")
                        + "\r\n.\r\n");
                out.flush();
                expect(in, "250");
                command(in, out, "QUIT", "221");
            } finally {
                socket.close();
            }
        }

        private void command(java.io.BufferedReader in, java.io.Writer out, String line, String code) throws IOException {
            out.write(line + "\r\n");
            out.flush();
            expect(in, code);
        }

        /** Reads a (possibly multi-line) reply and checks its code. */
        private void expect(java.io.BufferedReader in, String code) throws IOException {
            String line;
            do {
                line = in.readLine();
                if (line == null) {
                    throw new IOException("The mail server closed the connection");
                }
            } while (line.length() > 3 && line.charAt(3) == '-');
            if (!line.startsWith(code)) {
                throw new IOException("Mail server said: " + line);
            }
        }
    }

    // ===================== MiningJob =====================
    /** One piece of work for the miner thread: some data to put into a new block. */
    public static class MiningJob {
        private final BlockData data;
        private final ResultHandler<Block> handler;

        public MiningJob(BlockData data, ResultHandler<Block> handler) {
            this.data = data;
            this.handler = handler;
        }

        public BlockData getData() {
            return data;
        }

        public ResultHandler<Block> getHandler() {
            return handler;
        }
    }

    // ===================== MiningQueue =====================
    /**
     * Inter-thread communication (Unit 3): producer / consumer with wait() and notifyAll().
     * The user interface (producer) puts jobs in; the miner thread (consumer) sleeps in take()
     * until a job arrives. A LinkedList (Unit 4) is the queue.
     */
    public static class MiningQueue {
        private final LinkedList<MiningJob> jobs = new LinkedList<>();

        public synchronized void put(MiningJob job) {
            jobs.addLast(job);
            notifyAll(); // wake up the sleeping miner
        }

        public synchronized MiningJob take() throws InterruptedException {
            while (jobs.isEmpty()) {
                wait(); // releases the lock and sleeps until notifyAll()
            }
            return jobs.removeFirst();
        }

        public synchronized int size() {
            return jobs.size();
        }
    }

    // ===================== CertChainService =====================
    /**
     * The "backend". The user interface only talks to this class.
     *
     * Three parties:
     *   Authority - creates issuer accounts (needs the authority key)
     *   Issuer    - registers certificates and answers verification requests (needs its own key)
     *   Company   - verifies documents (no key needed)
     */
    public static class CertChainService {
        public static final String ADMIN_KEY =
                System.getenv("CERT_ADMIN_KEY") != null ? System.getenv("CERT_ADMIN_KEY") : "admin123";
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

        private final File storageDir;
        private final File requestDir;
        private final Database database;
        private final BlockRepository blockRepo;
        private final IssuerRepository issuerRepo;
        private final RequestRepository requestRepo;
        private final AccountRepository accountRepo;
        private final SignatureStore sigStore;
        private final Mailer mailer = new Mailer();
        private final Map<String, String[]> otps = new Hashtable<>(); // key -> {codeHash, expiresMillis, tries, sentMillis}
        private final Map<String, PrivateKey> unlockedKeys = new Hashtable<>();
        private final Map<String, Account> accounts = new Hashtable<>();
        private final Map<String, int[]> failedLogins = new Hashtable<>(); // username -> {count, lockedUntil seconds}
        private final Blockchain chain;
        private final MiningQueue queue = new MiningQueue();
        private final ActivityLog log = new ActivityLog();
        private final CertificateRenderer renderer = new CertificateRenderer();
        private final Map<String, Issuer> issuers = new Hashtable<>();
        private final Thread minerThread;
        private ChainWatchdog watchdog;
        private int tamperedIndex = -1;
        private String tamperedOriginal = "";

        public CertChainService(File dataDir) throws SQLException, IOException {
            this.storageDir = new File(dataDir, "certificates");
            this.requestDir = new File(dataDir, "requests");
            if (!storageDir.isDirectory() && !storageDir.mkdirs()) {
                throw new IOException("Cannot create folder " + storageDir);
            }
            if (!requestDir.isDirectory() && !requestDir.mkdirs()) {
                throw new IOException("Cannot create folder " + requestDir);
            }
            this.database = new Database(new File(dataDir, "certchain.db"));
            this.blockRepo = new BlockRepository(database);
            this.issuerRepo = new IssuerRepository(database);
            this.requestRepo = new RequestRepository(database);
            this.accountRepo = new AccountRepository(database);
            this.sigStore = new SignatureStore(database);
            this.chain = new Blockchain(blockRepo);
            for (Issuer i : issuerRepo.findAll()) {
                issuers.put(i.getId(), i);
            }
            for (Account a : accountRepo.findAll()) {
                accounts.put(a.getUsername(), a);
            }
            boolean hasAuthority = false;
            for (Account a : accounts.values()) {
                hasAuthority |= a.getRole() == Role.AUTHORITY;
            }
            if (!hasAuthority) {
                // the regulator account exists from the very first start; its password is the authority key
                Account authority = newAccount(AUTHORITY_USER, Role.AUTHORITY, ADMIN_KEY, "Authority", "");
                accountRepo.save(authority);
                accounts.put(authority.getUsername(), authority);
            }
            // The miner runs in its own thread and sleeps while the queue is empty.
            minerThread = new Thread(new Miner(chain, queue, log), "miner");
            minerThread.setDaemon(true);
            minerThread.setPriority(Thread.NORM_PRIORITY + 1);
            minerThread.start();
            log.add("Started. Database: " + database.getFile().getName() + ", " + chain.size() + " block(s)");
        }

        // ------------------------------------------------------------------ small helpers
        private static String today() {
            return LocalDate.now().format(DATE);
        }

        private static String require(String value, String label) throws ValidationException {
            if (value == null || value.trim().isEmpty()) {
                throw new ValidationException(label + " is required");
            }
            return value.trim();
        }

        private static String opt(String value) {
            return value == null ? "" : value.trim();
        }

        private static CertChainException dbError(SQLException e) {
            return new CertChainException("Database error: " + e.getMessage(), e);
        }

        private String uniqueCertId() {
            String id;
            do {
                id = Ids.newId("CV-", 10);
            } while (chain.hasCertId(id));
            return id;
        }

        public ActivityLog getLog() {
            return log;
        }

        public File getDatabaseFile() {
            return database.getFile();
        }

        // ------------------------------------------------------------------ digital signatures
        /** Makes sure the issuer has a key pair and keeps the unlocked private key in memory while signed in. */
        private void unlockSigningKey(Issuer issuer, String password) throws CertChainException {
            try {
                String[] row = sigStore.findKey(issuer.getId());
                if (row == null) {
                    KeyPair pair = Signer.generate();
                    String pub = Signer.publicKeyText(pair);
                    sigStore.saveKey(issuer.getId(), pub, Signer.lock(pair.getPrivate(), password));
                    unlockedKeys.put(issuer.getId(), pair.getPrivate());
                    log.add("Created signing key " + Signer.fingerprint(pub) + " for " + issuer.getName());
                } else {
                    unlockedKeys.put(issuer.getId(), Signer.unlock(row[2], password));
                }
            } catch (SQLException e) {
                throw dbError(e);
            }
        }

        public void lockSigningKey(Account account) {
            if (account != null && !account.getIssuerId().isEmpty()) {
                unlockedKeys.remove(account.getIssuerId());
            }
        }

        private PrivateKey signingKey(Issuer issuer) throws CertChainException {
            PrivateKey k = unlockedKeys.get(issuer.getId());
            if (k == null) {
                throw new AuthenticationException("Your signing key is locked. Sign out and sign in again.");
            }
            return k;
        }

        /** Signs the statement of a record (docHash is not part of it). */
        private String signRecord(Issuer issuer, BlockData data) throws CertChainException {
            return Signer.sign(signingKey(issuer), Signer.statement(data));
        }

        private void storeSignature(String docHash, String issuerId, String signature) throws CertChainException {
            try {
                if (sigStore.meta("sig_start") == null) {
                    sigStore.setMeta("sig_start", String.valueOf(chain.size()));
                }
                sigStore.save(new String[]{docHash, issuerId, signature});
            } catch (SQLException e) {
                throw dbError(e);
            }
        }

        public String publicKeyFingerprint(Issuer issuer) {
            try {
                String[] row = sigStore.findKey(issuer.getId());
                return row == null ? "" : Signer.fingerprint(row[1]);
            } catch (SQLException e) {
                return "";
            }
        }

        /** Checks the issuer's signature of one block: {state, info}. */
        private String[] checkSignature(Block block) {
            BlockData d = block.getData();
            try {
                String[] sig = sigStore.findById(d.getDocHash());
                String[] key = sigStore.findKey(d.getIssuerId());
                if (sig == null) {
                    String start = sigStore.meta("sig_start");
                    if (start != null && block.getIndex() >= Integer.parseInt(start)) {
                        return new String[]{"INVALID", "The digital signature of this record is missing."};
                    }
                    return new String[]{"UNSIGNED", "Registered before digital signatures were introduced."};
                }
                if (key == null || !sig[1].equals(d.getIssuerId())
                        || !Signer.verify(key[1], Signer.statement(d), sig[2])) {
                    return new String[]{"INVALID", "The digital signature does NOT match the certificate details."};
                }
                return new String[]{"VALID", "Signed by " + d.getIssuerName() + " (key " + Signer.fingerprint(key[1]) + ")"};
            } catch (SQLException | RuntimeException e) {
                return new String[]{"INVALID", "The signature could not be checked: " + e.getMessage()};
            }
        }

        private VerificationResult attachSignature(VerificationResult result, Block block) {
            if (block.getData().getIssuerId().isEmpty()) {
                return result;
            }
            String[] c = checkSignature(block);
            if ("INVALID".equals(c[0]) && !block.getData().isRejection()
                    && result.getVerdict() == Verdict.AUTHENTIC) {
                return new VerificationResult(Verdict.ERROR, "Digital signature check FAILED: " + c[1]
                        + " Do not trust this certificate.", result.getDocHash()).withBlock(block).withSignature(c[0], c[1]);
            }
            return result.withSignature(c[0], c[1]);
        }

        // ------------------------------------------------------------------ accounts
        public static final String AUTHORITY_USER = "authority";
        private static final int HASH_ROUNDS = 20000;
        private static final int MAX_FAILED = 5;
        private static final int LOCK_SECONDS = 30;

        /** Salted password hash: SHA-256 repeated many times, so guessing passwords is slow. */
        private static String hashPassword(String password, String salt) {
            String h = Hasher.sha256(salt + ":" + password);
            for (int i = 0; i < HASH_ROUNDS; i++) {
                h = Hasher.sha256(h + salt);
            }
            return h;
        }

        private static Account newAccount(String username, Role role, String password, String display, String issuerId) {
            String salt = Ids.newKey();
            return new Account(username, role, salt, hashPassword(password, salt), display, issuerId, today());
        }

        private static String cleanUsername(String username) throws ValidationException {
            String u = require(username, "Username").toLowerCase();
            if (!u.matches("[a-z0-9._-]{3,24}")) {
                throw new ValidationException("Username must be 3-24 characters: letters, digits, dot, dash or underscore");
            }
            return u;
        }

        private static void checkPassword(String password) throws ValidationException {
            if (password == null || password.length() < 6) {
                throw new ValidationException("Password must be at least 6 characters");
            }
        }

        /** Signs a user in to the portal of the chosen role. */
        public Account login(String username, String password, Role role) throws CertChainException {
            String u = username == null ? "" : username.trim().toLowerCase();
            if (u.isEmpty() || password == null || password.isEmpty()) {
                throw new AuthenticationException("Enter your username and password");
            }
            int[] failed = failedLogins.get(u);
            long now = System.currentTimeMillis() / 1000;
            if (failed != null && failed[1] > now) {
                throw new AuthenticationException("Too many wrong attempts. Try again in " + (failed[1] - now) + " seconds.");
            }
            Account a = accounts.get(u);
            boolean ok = false;
            if (a != null) {
                byte[] given = hashPassword(password, a.getSalt()).getBytes(StandardCharsets.UTF_8);
                ok = MessageDigest.isEqual(given, a.getPasswordHash().getBytes(StandardCharsets.UTF_8));
            }
            if (!ok) {
                int[] f = failed == null ? new int[2] : failed;
                f[0]++;
                if (f[0] >= MAX_FAILED) {
                    f[0] = 0;
                    f[1] = (int) (now + LOCK_SECONDS);
                }
                failedLogins.put(u, f);
                throw new AuthenticationException("Wrong username or password");
            }
            failedLogins.remove(u);
            if (a.getRole() != role) {
                throw new AuthenticationException("This account belongs to the " + a.getRole().getLabel()
                        + " portal. Choose \"" + a.getRole().getLabel() + "\" above.");
            }
            if (a.getRole() == Role.ISSUER) {
                Issuer issuer = issuers.get(a.getIssuerId());
                if (issuer != null) {
                    unlockSigningKey(issuer, password);
                }
            }
            log.add(a.getRole().getLabel() + " signed in: " + a.getUsername());
            return a;
        }

        private boolean passwordMatches(Account a, String password) {
            if (password == null) {
                return false;
            }
            byte[] given = hashPassword(password, a.getSalt()).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(given, a.getPasswordHash().getBytes(StandardCharsets.UTF_8));
        }

        /** Changes the password of the signed-in account. Issuer signing keys are re-protected with the new password. */
        public Account changePassword(Account account, String oldPassword, String newPassword, String otp,
                                      String emailIfMissing) throws CertChainException {
            Account current = accounts.get(account.getUsername());
            if (current == null || !passwordMatches(current, oldPassword)) {
                throw new AuthenticationException("The current password is wrong");
            }
            checkPassword(newPassword);
            String email = current.getEmail().isEmpty() ? cleanEmail(emailIfMissing) : current.getEmail();
            checkOtp("change", email, otp);
            if (newPassword.equals(oldPassword)) {
                throw new ValidationException("The new password must be different from the old one");
            }
            String newSalt = Ids.newKey();
            Account changed = current.withPassword(newSalt, hashPassword(newPassword, newSalt))
                    .withContact(email, current.getPhone());
            try {
                if (current.getRole() == Role.ISSUER) {
                    String[] key = sigStore.findKey(current.getIssuerId());
                    if (key != null) {
                        PrivateKey pk = Signer.unlock(key[2], oldPassword);
                        sigStore.saveKey(key[0], key[1], Signer.lock(pk, newPassword));
                        unlockedKeys.put(current.getIssuerId(), pk);
                    }
                }
                accountRepo.save(changed);
            } catch (SQLException e) {
                throw dbError(e);
            }
            accounts.put(changed.getUsername(), changed);
            log.add("Password changed for " + changed.getUsername());
            return changed;
        }

        /** Renames the signed-in account (the password is asked again for safety). */
        public Account changeUsername(Account account, String password, String newUsername) throws CertChainException {
            Account current = accounts.get(account.getUsername());
            if (current == null || !passwordMatches(current, password)) {
                throw new AuthenticationException("The password is wrong");
            }
            String u = cleanUsername(newUsername);
            if (u.equals(current.getUsername())) {
                throw new ValidationException("That is already your username");
            }
            if (accounts.containsKey(u)) {
                throw new DuplicateRecordException("The username \"" + u + "\" is already taken");
            }
            Account renamed = current.withUsername(u);
            try {
                accountRepo.save(renamed);
                accountRepo.delete(current.getUsername());
            } catch (SQLException e) {
                throw dbError(e);
            }
            accounts.remove(current.getUsername());
            accounts.put(u, renamed);
            log.add("Username changed: " + current.getUsername() + " -> " + u);
            return renamed;
        }

        // ------------------------------------------------------------------ OTP (one-time codes by e-mail)
        private static final int OTP_MINUTES = 5;
        private static final int OTP_RESEND_SECONDS = 30;
        private static final int OTP_MAX_TRIES = 5;
        private final SecureRandom otpRandom = new SecureRandom();

        public boolean emailConfigured() {
            return mailer.isConfigured();
        }

        public static String cleanEmail(String email) throws ValidationException {
            String e = require(email, "E-mail address").toLowerCase();
            if (!e.matches("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")) {
                throw new ValidationException("Enter a valid e-mail address, e.g. hr@company.com");
            }
            return e;
        }

        /** Optional mobile number: 10 to 13 digits, may start with +. */
        public static String cleanPhone(String phone) throws ValidationException {
            String p = opt(phone).replace(" ", "").replace("-", "");
            if (!p.isEmpty() && !p.matches("\\+?[0-9]{10,13}")) {
                throw new ValidationException("Mobile number must have 10 to 13 digits");
            }
            return p;
        }

        private boolean emailInUse(String email) {
            for (Account a : accounts.values()) {
                if (a.getEmail().equalsIgnoreCase(email)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Creates a 6-digit code and e-mails it. Returns "" when the e-mail was sent, or the code itself in demo
         * mode (no mail server configured) so the screen can show it.
         */
        private String sendOtp(String purpose, String email) throws CertChainException {
            String key = purpose + "|" + email;
            long now = System.currentTimeMillis();
            String[] old = otps.get(key);
            if (old != null && now - Long.parseLong(old[3]) < OTP_RESEND_SECONDS * 1000L) {
                long wait = OTP_RESEND_SECONDS - (now - Long.parseLong(old[3])) / 1000;
                throw new ValidationException("Please wait " + wait + " seconds before asking for another code");
            }
            String code = String.format("%06d", otpRandom.nextInt(1000000));
            String[] entry = {Hasher.sha256(key + ":" + code), String.valueOf(now + OTP_MINUTES * 60000L), "0", String.valueOf(now)};
            if (mailer.isConfigured()) {
                try {
                    mailer.send(email, "CertChain verification code",
                            "Your CertChain verification code is " + code + "\n\nIt is valid for " + OTP_MINUTES
                                    + " minutes. Never share this code with anyone.\nIf you did not ask for it, ignore this e-mail.\n");
                } catch (IOException e) {
                    throw new CertChainException("The e-mail could not be sent: " + e.getMessage(), e);
                }
                otps.put(key, entry);
                log.add("OTP e-mailed to " + Account.maskEmail(email));
                return "";
            }
            otps.put(key, entry);
            log.add("DEMO MODE: no mail server set, OTP for " + Account.maskEmail(email) + " is shown on screen");
            return code;
        }

        private void checkOtp(String purpose, String email, String code) throws CertChainException {
            String key = purpose + "|" + email;
            String[] entry = otps.get(key);
            if (entry == null) {
                throw new AuthenticationException("Ask for an OTP first");
            }
            if (System.currentTimeMillis() > Long.parseLong(entry[1])) {
                otps.remove(key);
                throw new AuthenticationException("The OTP has expired. Ask for a new one.");
            }
            String given = Hasher.sha256(key + ":" + (code == null ? "" : code.trim()));
            if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), entry[0].getBytes(StandardCharsets.UTF_8))) {
                int tries = Integer.parseInt(entry[2]) + 1;
                if (tries >= OTP_MAX_TRIES) {
                    otps.remove(key);
                    throw new AuthenticationException("Too many wrong codes. Ask for a new OTP.");
                }
                entry[2] = String.valueOf(tries);
                throw new AuthenticationException("Wrong OTP (" + (OTP_MAX_TRIES - tries) + " tries left)");
            }
            otps.remove(key);
        }

        /** Step 1 of creating a verifier account: sends the code to the company e-mail. */
        public String requestSignupOtp(String email, String phone) throws CertChainException {
            String e = cleanEmail(email);
            cleanPhone(phone);
            if (emailInUse(e)) {
                throw new DuplicateRecordException("An account with this e-mail already exists. Use \"Forgot password\" instead.");
            }
            return sendOtp("signup", e);
        }

        /** Step 1 of a password change inside the app. The code goes to the e-mail on file (or the one typed if none). */
        public String requestChangeOtp(Account account, String emailIfMissing) throws CertChainException {
            Account current = accounts.get(account.getUsername());
            String email = current == null || current.getEmail().isEmpty() ? cleanEmail(emailIfMissing) : current.getEmail();
            return sendOtp("change", email);
        }

        /** Forgot password, step 1: returns {maskedEmail, demoCodeOrEmpty}. */
        public String[] requestResetOtp(String username) throws CertChainException {
            Account a = accounts.get(opt(username).toLowerCase());
            if (a == null || a.getEmail().isEmpty()) {
                throw new ValidationException("No e-mail is registered for this username, so a code cannot be sent."
                        + " Ask the authority (issuers) or create a new account (companies).");
            }
            String demo = sendOtp("reset", a.getEmail());
            return new String[]{Account.maskEmail(a.getEmail()), demo};
        }

        /** Forgot password, step 2: checks the code and sets the new password. */
        public Account resetPassword(String username, String otp, String newPassword) throws CertChainException {
            Account a = accounts.get(opt(username).toLowerCase());
            if (a == null || a.getEmail().isEmpty()) {
                throw new ValidationException("No e-mail is registered for this username");
            }
            checkPassword(newPassword);
            checkOtp("reset", a.getEmail(), otp);
            String newSalt = Ids.newKey();
            Account changed = a.withPassword(newSalt, hashPassword(newPassword, newSalt));
            try {
                if (a.getRole() == Role.ISSUER) {
                    renewSigningKey(a.getIssuerId(), newPassword);
                }
                accountRepo.save(changed);
            } catch (SQLException e) {
                throw dbError(e);
            }
            accounts.put(changed.getUsername(), changed);
            failedLogins.remove(changed.getUsername());
            log.add("Password reset by e-mail OTP for " + changed.getUsername());
            return changed;
        }

        /**
         * The old private key was locked with the forgotten password and cannot be opened. The institution proved
         * its identity with the e-mail OTP, so it gets a fresh key pair and its records are signed again.
         */
        private void renewSigningKey(String issuerId, String newPassword) throws CertChainException, SQLException {
            KeyPair pair = Signer.generate();
            sigStore.saveKey(issuerId, Signer.publicKeyText(pair), Signer.lock(pair.getPrivate(), newPassword));
            int count = 0;
            for (Block b : chain.snapshot()) {
                BlockData d = b.getData();
                if (issuerId.equals(d.getIssuerId())) {
                    sigStore.save(new String[]{d.getDocHash(), issuerId, Signer.sign(pair.getPrivate(), Signer.statement(d))});
                    count++;
                }
            }
            log.add("New signing key created for " + issuerId + "; " + count + " record(s) signed again");
        }

        /** A company creates its own verifier account. */
        public Account registerVerifier(String company, String username, String password, String email, String phone,
                                        String otp) throws CertChainException {
            String name = require(company, "Company name");
            String u = cleanUsername(username);
            checkPassword(password);
            String mail = cleanEmail(email);
            String mobile = cleanPhone(phone);
            if (emailInUse(mail)) {
                throw new DuplicateRecordException("An account with this e-mail already exists");
            }
            if (accounts.containsKey(u)) {
                throw new DuplicateRecordException("The username \"" + u + "\" is already taken");
            }
            checkOtp("signup", mail, otp);
            Account a = newAccount(u, Role.VERIFIER, password, name, "").withContact(mail, mobile);
            try {
                accountRepo.save(a);
            } catch (SQLException e) {
                throw dbError(e);
            }
            accounts.put(u, a);
            log.add("New verifier account: " + u + " (" + name + ")");
            return a;
        }

        /** Only the authority can create an institution together with its login. */
        public Pair<Issuer, Account> createIssuerAccount(Account actor, String institution, String username, String password,
                                                         String email, String phone) throws CertChainException {
            if (actor == null || actor.getRole() != Role.AUTHORITY) {
                throw new AuthenticationException("Only the authority can create issuer accounts");
            }
            String u = cleanUsername(username);
            checkPassword(password);
            if (accounts.containsKey(u)) {
                throw new DuplicateRecordException("The username \"" + u + "\" is already taken");
            }
            String mail = cleanEmail(email);
            String mobile = cleanPhone(phone);
            if (emailInUse(mail)) {
                throw new DuplicateRecordException("This e-mail already belongs to another account");
            }
            Issuer issuer = createIssuer(ADMIN_KEY, institution).getFirst();
            Account a = newAccount(u, Role.ISSUER, password, issuer.getName(), issuer.getId()).withContact(mail, mobile);
            try {
                accountRepo.save(a);
            } catch (SQLException e) {
                throw dbError(e);
            }
            accounts.put(u, a);
            return new Pair<>(issuer, a);
        }

        /** The institution that belongs to an issuer account. */
        public Issuer issuerFor(Account account) throws NotFoundException {
            Issuer i = account == null ? null : issuers.get(account.getIssuerId());
            if (i == null) {
                throw new NotFoundException("This account has no institution");
            }
            return i;
        }

        public List<Account> listAccounts(Role role) {
            List<Account> list = new ArrayList<>();
            for (Account a : accounts.values()) {
                if (a.getRole() == role) {
                    list.add(a);
                }
            }
            list.sort((x, y) -> x.getUsername().compareTo(y.getUsername()));
            return list;
        }

        // ------------------------------------------------------------------ authority
        /** Returns the new issuer together with its secret key (the key is shown only once). */
        public Pair<Issuer, String> createIssuer(String adminKey, String name) throws CertChainException {
            if (adminKey == null || !adminKey.equals(ADMIN_KEY)) {
                throw new AuthenticationException("Wrong authority key");
            }
            String clean = require(name, "Institution name");
            Iterator<Issuer> it = issuers.values().iterator();
            while (it.hasNext()) {
                if (it.next().getName().equalsIgnoreCase(clean)) {
                    throw new DuplicateRecordException("An issuer named \"" + clean + "\" already exists");
                }
            }
            String key = Ids.newKey();
            Issuer issuer = new Issuer(Ids.newId("ISS-", 6), clean, Hasher.sha256(key), today());
            try {
                issuerRepo.save(issuer);
            } catch (SQLException e) {
                throw dbError(e);
            }
            issuers.put(issuer.getId(), issuer);
            log.add("Authority approved issuer " + issuer.getName() + " (" + issuer.getId() + ")");
            return new Pair<>(issuer, key);
        }

        public List<Issuer> listIssuers() {
            List<Issuer> list = new ArrayList<>(issuers.values());
            list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return list;
        }

        /** Sorted institution names without duplicates (TreeSet). */
        public TreeSet<String> issuerNames() {
            TreeSet<String> names = new TreeSet<>();
            for (Issuer i : issuers.values()) {
                names.add(i.getName());
            }
            return names;
        }

        // ------------------------------------------------------------------ issuer
        public Issuer authenticate(String key) throws AuthenticationException {
            if (key == null || key.trim().isEmpty()) {
                throw new AuthenticationException("Enter your issuer key");
            }
            byte[] given = Hasher.sha256(key.trim()).getBytes(StandardCharsets.UTF_8);
            for (Issuer issuer : issuers.values()) {
                if (MessageDigest.isEqual(given, issuer.getKeyHash().getBytes(StandardCharsets.UTF_8))) {
                    return issuer;
                }
            }
            throw new AuthenticationException("Invalid issuer key");
        }

        /**
         * Generates a brand-new certificate picture and registers it. The block is mined by the miner
         * thread, so this method returns immediately and the handler is called later.
         */
        public void issueCertificate(Issuer issuer, String holder, String course, String grade, String year,
                                     ResultHandler<Pair<CertificateData, File>> handler)
                throws CertChainException, IOException {
            issueCertificate(issuer, holder, course, grade, year, "", handler);
        }

        /**
         * Overloaded: photoSource is a link (http/https) or a file path of the holder's photo. The photo is drawn
         * on the certificate, so it is covered by the file fingerprint stored on the blockchain.
         */
        public void issueCertificate(Issuer issuer, String holder, String course, String grade, String year,
                                     String photoSource, ResultHandler<Pair<CertificateData, File>> handler)
                throws CertChainException, IOException {
            BufferedImage photo = loadPhoto(photoSource);
            String h = require(holder, "Holder name");
            String c = require(course, "Course / examination");
            String g = opt(grade);
            String y = opt(year);
            String certId = uniqueCertId();
            String date = today();

            CertificateData draft = new CertificateData("", issuer.getId(), issuer.getName(), date,
                    certId, h, c, g, y, "", "generated");
            String signature = signRecord(issuer, draft);
            byte[] png = renderer.render(issuer.getName(), certId, h, c, g, y, date, signature,
                    publicKeyFingerprint(issuer), photo);
            File picture = new File(storageDir, certId + ".png");
            FileUtil.write(picture, png);

            CertificateData data = new CertificateData(Hasher.sha256(png), issuer.getId(), issuer.getName(), date,
                    certId, h, c, g, y, "", "generated");
            storeSignature(data.getDocHash(), issuer.getId(), signature);
            log.add("Mining a block for " + certId + " ...");
            queue.put(new MiningJob(data, new ResultHandler<Block>() {
                @Override
                public void onSuccess(Block block) {
                    handler.onSuccess(new Pair<>(data, picture));
                }

                @Override
                public void onError(Exception error) {
                    picture.delete();
                    handler.onError(error);
                }
            }));
        }

        private static final int MAX_PHOTO_BYTES = 3 * 1024 * 1024;

        /** Reads the holder's photo from a link or a file. Returns null when no photo was given. */
        public static BufferedImage loadPhoto(String source) throws CertChainException {
            String src = source == null ? "" : source.trim();
            if (src.isEmpty()) {
                return null;
            }
            try {
                byte[] bytes;
                String lower = src.toLowerCase();
                if (lower.startsWith("http://") || lower.startsWith("https://")) {
                    java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(src).openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(15000);
                    con.setRequestProperty("User-Agent", "CertChain");
                    if (con.getResponseCode() != 200) {
                        throw new ValidationException("The photo link did not open (error " + con.getResponseCode() + ")");
                    }
                    bytes = readLimited(con.getInputStream());
                } else {
                    File f = new File(src.startsWith("file:") ? src.substring(5) : src);
                    if (!f.isFile()) {
                        throw new ValidationException("Photo not found. Give a link starting with https:// or choose a file.");
                    }
                    bytes = readLimited(new java.io.FileInputStream(f));
                }
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (img == null) {
                    throw new ValidationException("The photo must be a JPG or PNG picture");
                }
                return img;
            } catch (IOException e) {
                throw new CertChainException("The photo could not be loaded: " + e.getMessage(), e);
            }
        }

        private static byte[] readLimited(java.io.InputStream in) throws IOException, ValidationException {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_PHOTO_BYTES) {
                        throw new ValidationException("The photo is too big (maximum 3 MB)");
                    }
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        }

        /** Registers a document the issuer already has (for example a scanned marks memo). */
        public void registerExisting(Issuer issuer, File document, String holder, String course, String grade,
                                     String year, String certNumber, ResultHandler<CertificateData> handler)
                throws CertChainException, IOException {
            String h = require(holder, "Holder name");
            String c = require(course, "Course / examination");
            if (document == null || !document.isFile()) {
                throw new ValidationException("Choose a document first");
            }
            String hash = Hasher.sha256(document);
            Block existing = chain.find(hash);
            if (existing != null && !existing.getData().isRejection()) {
                throw new DuplicateRecordException("This document is already registered");
            }
            CertificateData data = new CertificateData(hash, issuer.getId(), issuer.getName(), today(), uniqueCertId(),
                    h, c, opt(grade), opt(year), opt(certNumber), "uploaded");
            storeSignature(hash, issuer.getId(), signRecord(issuer, data));
            log.add("Mining a block for uploaded document " + document.getName() + " ...");
            queue.put(new MiningJob(data, new ResultHandler<Block>() {
                @Override
                public void onSuccess(Block block) {
                    handler.onSuccess(data);
                }

                @Override
                public void onError(Exception error) {
                    handler.onError(error);
                }
            }));
        }

        // ------------------------------------------------------------------ company: verify
        /** Overloaded verify (Unit 2): verify(File) checks a document ... */
        public VerificationResult verify(File document) throws CertChainException, IOException {
            return verify(document, "");
        }

        /**
         * Overloaded again: if the document is not registered, but we know which certificate it claims to be
         * (typed by the user, or part of the file name such as CV-1A2B3C4D5E.png), we compare it with the
         * registered original and report MODIFIED together with both hashes.
         */
        public VerificationResult verify(File document, String claimedId) throws CertChainException, IOException {
            String hash = Hasher.sha256(document);
            VerificationResult result = lookup(hash);
            if (result.getVerdict() != Verdict.NOT_VERIFIED) {
                return result;
            }
            String id = claimedId == null ? "" : claimedId.trim().toUpperCase();
            if (id.isEmpty()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("CV-[0-9A-F]{6,12}")
                        .matcher(document.getName().toUpperCase());
                if (m.find()) {
                    id = m.group();
                }
            }
            Block original = id.isEmpty() ? null : chain.findByCertId(id);
            if (original == null || original.getData().isRejection()) {
                return result;
            }
            List<String> notes = new ArrayList<>();
            File highlight = null;
            File stored = certificateFile(id);
            if (stored != null) {
                FileCompare compare = new FileCompare();
                notes = compare.compare(stored, document);
                highlight = compare.getHighlight();
            } else {
                notes.add("The original file is not stored here, so only the fingerprints can be compared.");
            }
            return new VerificationResult(Verdict.MODIFIED, "This document claims to be certificate " + id
                    + " but it has been MODIFIED. Its fingerprint is different from the one registered on the blockchain.",
                    hash).withBlock(original).withModification(id, original.getData().getDocHash(), notes, highlight);
        }

        /** ... and verify(String) checks a certificate ID. */
        public VerificationResult verify(String certId) throws CertChainException {
            String id = require(certId, "Certificate ID").toUpperCase();
            VerificationResult broken = checkChain("");
            if (broken != null) {
                return broken;
            }
            Block block = chain.findByCertId(id);
            if (block == null) {
                return new VerificationResult(Verdict.NOT_VERIFIED, "No certificate with this ID exists on the blockchain.", "");
            }
            VerificationResult result = attachSignature(new VerificationResult(Verdict.AUTHENTIC,
                    "This certificate is authentic and registered on the blockchain.", block.getData().getDocHash())
                    .withBlock(block), block);
            File picture = certificateFile(id);
            if (picture != null) {
                try {
                    result.withFileIntact(Hasher.sha256(picture).equals(block.getData().getDocHash()));
                } catch (IOException e) {
                    result.withFileIntact(Boolean.FALSE);
                }
            }
            return result;
        }

        private VerificationResult checkChain(String docHash) {
            ValidationReport report = chain.validate();
            if (!report.isValid()) {
                return new VerificationResult(Verdict.ERROR, "Blockchain integrity failed: " + report.getMessage(), docHash);
            }
            return null;
        }

        private VerificationResult lookup(String docHash) throws CertChainException {
            VerificationResult broken = checkChain(docHash);
            if (broken != null) {
                return broken;
            }
            Block block = chain.find(docHash);
            if (block != null && block.getData().isRejection()) {
                RejectionData r = (RejectionData) block.getData();
                return attachSignature(new VerificationResult(Verdict.FORGED, "The issuing institution (" + r.getIssuerName()
                        + ") has confirmed that this document is NOT genuine.", docHash).withBlock(block), block);
            }
            if (block != null) {
                return attachSignature(new VerificationResult(Verdict.AUTHENTIC,
                        "This certificate is authentic and registered on the blockchain.", docHash).withBlock(block), block);
            }
            try {
                VerificationRequest pending = requestRepo.findPendingByHash(docHash);
                if (pending != null) {
                    Issuer issuer = issuers.get(pending.getIssuerId());
                    return new VerificationResult(Verdict.PENDING, "A verification request for this document is already waiting for "
                            + (issuer == null ? "the issuer" : issuer.getName()) + ".", docHash)
                            .withRequestId(pending.getId());
                }
            } catch (SQLException e) {
                throw dbError(e);
            }
            return new VerificationResult(Verdict.NOT_VERIFIED, "Not registered on the blockchain. No issuing institution has "
                    + "confirmed this document. You can ask the issuer to verify it.", docHash);
        }

        // ------------------------------------------------------------------ company: ask an issuer
        public VerificationRequest submitRequest(String issuerId, String company, String candidate, String certNumber,
                                                 String course, String year, File document)
                throws CertChainException, IOException {
            Issuer issuer = issuers.get(issuerId);
            if (issuer == null) {
                throw new NotFoundException("Choose the issuing institution");
            }
            String comp = require(company, "Your company name");
            String cand = require(candidate, "Candidate name");
            if (document == null || !document.isFile()) {
                throw new ValidationException("Choose the document to verify");
            }
            String hash = Hasher.sha256(document);
            if (chain.find(hash) != null) {
                throw new DuplicateRecordException("This document already has an answer on the blockchain. Just verify it again.");
            }
            try {
                VerificationRequest pending = requestRepo.findPendingByHash(hash);
                if (pending != null && pending.getIssuerId().equals(issuerId)) {
                    return pending;
                }
                String id = Ids.newId("REQ-", 8);
                String ext = FileUtil.extension(document.getName());
                String stored = ext.isEmpty() ? id : id + "." + ext;
                FileUtil.copy(document, new File(requestDir, stored));
                VerificationRequest request = new VerificationRequest(id, issuerId, comp, cand, opt(certNumber),
                        opt(course), opt(year), hash, document.getName(), stored, RequestStatus.PENDING, "", today(), "", "");
                requestRepo.save(request);
                log.add("Request " + id + " sent to " + issuer.getName());
                return request;
            } catch (SQLException e) {
                throw dbError(e);
            }
        }

        public VerificationRequest getRequest(String requestId) throws CertChainException {
            try {
                VerificationRequest r = requestRepo.findById(require(requestId, "Request ID").toUpperCase());
                if (r == null) {
                    throw new NotFoundException("No request with this ID");
                }
                return r;
            } catch (SQLException e) {
                throw dbError(e);
            }
        }

        public String issuerNameOf(String issuerId) {
            Issuer i = issuers.get(issuerId);
            return i == null ? "" : i.getName();
        }

        // ------------------------------------------------------------------ issuer: answer requests
        public List<VerificationRequest> requestsFor(Issuer issuer) throws CertChainException {
            try {
                return requestRepo.findByIssuer(issuer.getId());
            } catch (SQLException e) {
                throw dbError(e);
            }
        }

        /** Confirms (approve = true) or rejects a request. The decision is mined into a block. */
        public void decide(Issuer issuer, String requestId, boolean approve, String remarks,
                           ResultHandler<Block> handler) throws CertChainException {
            VerificationRequest r = getRequest(requestId);
            if (!r.getIssuerId().equals(issuer.getId())) {
                throw new NotFoundException("Request not found");
            }
            if (r.getStatus() != RequestStatus.PENDING) {
                throw new DuplicateRecordException("This request was already decided");
            }
            String rem = opt(remarks);
            String certId = approve ? uniqueCertId() : "";
            BlockData data;
            if (approve) {
                data = new IssuerConfirmedCertificate(r.getDocHash(), issuer.getId(), issuer.getName(), today(), certId,
                        r.getCandidate(), r.getCourse(), "", r.getYear(), r.getCertNumber(), r.getId(), rem);
            } else {
                data = new RejectionData(r.getDocHash(), issuer.getId(), issuer.getName(), today(), r.getCandidate(),
                        r.getId(), rem.isEmpty() ? "Document is not genuine" : rem);
            }
            storeSignature(data.getDocHash(), issuer.getId(), signRecord(issuer, data));
            log.add("Recording decision for " + r.getId() + " ...");
            queue.put(new MiningJob(data, new ResultHandler<Block>() {
                @Override
                public void onSuccess(Block block) {
                    try {
                        r.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.REJECTED);
                        r.setRemarks(rem);
                        r.setDecidedOn(today());
                        r.setCertId(certId);
                        requestRepo.save(r);
                    } catch (SQLException e) {
                        handler.onError(e);
                        return;
                    }
                    handler.onSuccess(block);
                }

                @Override
                public void onError(Exception error) {
                    handler.onError(error);
                }
            }));
        }

        // ------------------------------------------------------------------ ledger and statistics
        public List<Block> ledger() {
            return chain.snapshot();
        }

        public ValidationReport validateChain() {
            ValidationReport report = chain.validate();
            log.add("Chain check: " + report.getMessage());
            return report;
        }

        /** Starts the low-priority background thread that re-checks the chain. */
        public void startWatchdog(long periodMillis, ResultHandler<ValidationReport> handler) {
            watchdog = new ChainWatchdog(chain, periodMillis, handler);
            watchdog.start();
        }

        public int blockCount() {
            return chain.size();
        }

        public int issuerCount() {
            return issuers.size();
        }

        /** Counts certificates with the generic method ofType (rejections and the genesis block are skipped). */
        public int certificateCount() {
            List<BlockData> all = new ArrayList<>();
            for (Block b : chain.snapshot()) {
                all.add(b.getData());
            }
            return GenericUtil.ofType(all, CertificateData.class).size();
        }

        public int pendingCount() {
            try {
                int count = 0;
                for (VerificationRequest r : requestRepo.findAll()) {
                    if (r.getStatus() == RequestStatus.PENDING) {
                        count++;
                    }
                }
                return count;
            } catch (SQLException e) {
                return 0;
            }
        }

        public File certificateFile(String certId) {
            File f = new File(storageDir, certId + ".png");
            return f.isFile() ? f : null;
        }

        public File requestFile(VerificationRequest request) {
            File f = new File(requestDir, request.getStoredAs());
            return f.isFile() ? f : null;
        }

        /** Writes the whole ledger as a text file using a byte stream. */
        public void exportLedger(File target) throws IOException {
            StringBuilder sb = new StringBuilder("CertChain ledger\n================\n\n");
            for (Block b : chain.snapshot()) {
                sb.append("Block #").append(b.getIndex()).append('\n')
                        .append("  Type     : ").append(b.getData().getType()).append('\n')
                        .append("  Details  : ").append(b.getData().describe()).append('\n')
                        .append("  Hash     : ").append(b.getHash()).append('\n')
                        .append("  Previous : ").append(b.getPreviousHash()).append('\n')
                        .append("  Nonce    : ").append(b.getNonce()).append("\n\n");
            }
            FileUtil.write(target, sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        // ------------------------------------------------------------------ tampering demo
        /** Edits a stored name directly in the database WITHOUT recomputing the hash. The chain check must catch it. */
        public synchronized String tamperDemo() throws CertChainException {
            if (tamperedIndex >= 0) {
                throw new ValidationException("Block #" + tamperedIndex + " is already tampered. Revert it first.");
            }
            for (Block b : chain.snapshot()) {
                if (b.getData() instanceof CertificateData) {
                    try {
                        tamperedIndex = b.getIndex();
                        tamperedOriginal = blockRepo.holderOf(tamperedIndex);
                        blockRepo.overwriteHolder(tamperedIndex, "TAMPERED NAME");
                        chain.reload();
                    } catch (SQLException e) {
                        tamperedIndex = -1;
                        throw dbError(e);
                    }
                    log.add("DEMO: holder name in block #" + tamperedIndex + " was edited directly in the database");
                    return "Block #" + tamperedIndex + " was edited. Validate the chain to see it detected.";
                }
            }
            throw new ValidationException("Issue a certificate first, then try the tampering demo");
        }

        public synchronized String revertTamper() throws CertChainException {
            if (tamperedIndex < 0) {
                throw new ValidationException("Nothing to revert");
            }
            try {
                blockRepo.overwriteHolder(tamperedIndex, tamperedOriginal);
                chain.reload();
            } catch (SQLException e) {
                throw dbError(e);
            }
            int index = tamperedIndex;
            tamperedIndex = -1;
            log.add("DEMO: block #" + index + " restored");
            return "Block #" + index + " restored.";
        }

        public synchronized boolean isTampered() {
            return tamperedIndex >= 0;
        }

        public void shutdown() {
            minerThread.interrupt();
            if (watchdog != null) {
                watchdog.interrupt();
            }
            database.close();
        }
    }

    // ===================== CertificateRenderer =====================
    /**
     * Draws the certificate with AWT (Graphics2D) and returns it as PNG bytes.
     * Only built-in Java fonts are used, so it works on every computer.
     */
    public static class CertificateRenderer {
        private static final int W = 1600;
        private static final int H = 1131;
        private static final Color NAVY = new Color(0x0F2A4A);
        private static final Color GOLD = new Color(0xB8892B);
        private static final Color PAPER = new Color(0xFBF9F3);
        private static final Color GREY = new Color(0x555555);

        public byte[] render(String issuer, String certId, String holder, String course, String grade, String year,
                             String date, String signature, String fingerprint, BufferedImage photo) throws IOException {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g.setColor(PAPER);
                g.fillRect(0, 0, W, H);
                g.setColor(NAVY);
                g.setStroke(new BasicStroke(14));
                g.drawRect(40, 40, W - 80, H - 80);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(3));
                g.drawRect(66, 66, W - 132, H - 132);

                centered(g, issuer.toUpperCase(), "Serif", Font.BOLD, 58, photo == null ? 1300 : 1000, NAVY, 190);
                g.setColor(GOLD);
                g.setStroke(new BasicStroke(2));
                g.drawLine(W / 2 - 230, 215, W / 2 + 230, 215);

                centered(g, "Certificate of Achievement", "Serif", Font.BOLD | Font.ITALIC, 76, 1300, GOLD, 330);
                centered(g, "This is to certify that", "SansSerif", Font.ITALIC, 30, 1300, GREY, 410);
                centered(g, holder, "Serif", Font.BOLD | Font.ITALIC, 74, 1300, NAVY, 520);
                g.setColor(NAVY);
                g.setStroke(new BasicStroke(1.6f));
                g.drawLine(W / 2 - 380, 548, W / 2 + 380, 548);

                centered(g, "has successfully completed / passed", "SansSerif", Font.ITALIC, 30, 1300, GREY, 618);
                centered(g, course, "SansSerif", Font.BOLD, 46, 1300, NAVY, 692);

                String extra = "";
                if (!grade.isEmpty()) {
                    extra += "Grade / Marks: " + grade;
                }
                if (!year.isEmpty()) {
                    extra += (extra.isEmpty() ? "" : "        |        ") + "Year: " + year;
                }
                if (!extra.isEmpty()) {
                    centered(g, extra, "SansSerif", Font.PLAIN, 30, 1300, GREY, 770);
                }

                // bottom left: date and id
                g.setColor(GREY);
                g.setFont(new Font("SansSerif", Font.PLAIN, 25));
                g.drawString("Issued on: " + date, 140, 930);
                g.setColor(NAVY);
                g.setFont(new Font("SansSerif", Font.BOLD, 25));
                g.drawString("Certificate ID: " + certId, 140, 970);

                // holder photo (top right, inside the border)
                if (photo != null) {
                    int pw = 135;
                    int ph = 170;
                    int px = W - 235;
                    int py = 82;
                    double scale = Math.max((double) pw / photo.getWidth(), (double) ph / photo.getHeight());
                    int sw = (int) Math.round(photo.getWidth() * scale);
                    int sh = (int) Math.round(photo.getHeight() * scale);
                    java.awt.Shape oldClip = g.getClip();
                    g.setClip(px, py, pw, ph);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(photo, px + (pw - sw) / 2, py + (ph - sh) / 2, sw, sh, null);
                    g.setClip(oldClip);
                    g.setColor(NAVY);
                    g.setStroke(new BasicStroke(4));
                    g.drawRect(px, py, pw, ph);
                }

                // digital signature block (bottom left, under the ID)
                g.setColor(GOLD);
                g.setFont(new Font("SansSerif", Font.BOLD, 20));
                g.drawString("Digitally signed by " + issuer + "  (ECDSA P-256)", 140, 1010);
                g.setColor(GREY);
                g.setFont(new Font("Monospaced", Font.PLAIN, 18));
                String shown = signature.length() > 44 ? signature.substring(0, 44) + "..." : signature;
                g.drawString("Signature: " + shown + "   Key: " + fingerprint, 140, 1038);

                // centre seal
                int cx = W / 2;
                int cy = 930;
                g.setColor(GOLD);
                g.fillOval(cx - 84, cy - 84, 168, 168);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(3));
                g.drawOval(cx - 70, cy - 70, 140, 140);
                centered(g, "VERIFIED", "SansSerif", Font.BOLD, 24, 130, Color.WHITE, cy - 2);
                centered(g, "ON BLOCKCHAIN", "SansSerif", Font.PLAIN, 14, 130, Color.WHITE, cy + 26);

                // bottom right: how to verify
                g.setColor(NAVY);
                g.setStroke(new BasicStroke(3));
                g.drawRoundRect(W - 470, 860, 330, 150, 20, 20);
                g.setFont(new Font("SansSerif", Font.BOLD, 24));
                g.drawString("Verify this certificate", W - 440, 905);
                g.setColor(GREY);
                g.setFont(new Font("SansSerif", Font.PLAIN, 22));
                g.drawString("Open CertChain > Verify", W - 440, 945);
                g.drawString("and enter the ID shown.", W - 440, 978);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }

        /** Draws text centred on the page, shrinking the font until it fits in maxWidth. */
        private void centered(Graphics2D g, String text, String family, int style, int size, int maxWidth, Color color,
                              int baselineY) {
            Font font = new Font(family, style, size);
            FontMetrics fm = g.getFontMetrics(font);
            while (fm.stringWidth(text) > maxWidth && size > 14) {
                size -= 2;
                font = new Font(family, style, size);
                fm = g.getFontMetrics(font);
            }
            g.setFont(font);
            g.setColor(color);
            g.drawString(text, (W - fm.stringWidth(text)) / 2, baselineY);
        }
    }

}
