package certchain.core;

import certchain.engine.Engine.*;
import certchain.ui.Screens.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import javax.imageio.ImageIO;

/** All classes of the "core" part of CertChain, kept in one file. */
public final class Core {
    private Core() {
    }

    // ===================== AuthenticationException =====================
    /** Thrown when an issuer key or the authority key is wrong. */
    public static class AuthenticationException extends CertChainException {
        public AuthenticationException(String message) {
            super(message);
        }
    }

    // ===================== CertChainException =====================
    /** Base class of all application exceptions (checked). Unit 2: user defined exceptions. */
    public static class CertChainException extends Exception {
        public CertChainException(String message) {
            super(message);
        }

        public CertChainException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ===================== DuplicateRecordException =====================
    /** Thrown when a document or issuer is already registered. */
    public static class DuplicateRecordException extends CertChainException {
        public DuplicateRecordException(String message) {
            super(message);
        }
    }

    // ===================== NotFoundException =====================
    /** Thrown when a certificate, request or issuer does not exist. */
    public static class NotFoundException extends CertChainException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    // ===================== TamperedChainException =====================
    /** Thrown when the blockchain fails validation (someone edited a block). */
    public static class TamperedChainException extends CertChainException {
        private final int blockIndex;

        public TamperedChainException(String message, int blockIndex) {
            super(message);
            this.blockIndex = blockIndex;
        }

        public int getBlockIndex() {
            return blockIndex;
        }
    }

    // ===================== ValidationException =====================
    /** Thrown when the user typed missing or invalid input. */
    public static class ValidationException extends CertChainException {
        public ValidationException(String message) {
            super(message);
        }
    }

    // ===================== ActivityLog =====================
    /**
     * Thread-safe activity log. A Vector is synchronized, so the miner thread, the watchdog thread
     * and the user interface can all write to it (Unit 4: Vector).
     */
    public static class ActivityLog {
        /** Listener interface: the user interface registers one to show new lines. */
        public interface Listener {
            void logged(String line);
        }

        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

        private final Vector<String> lines = new Vector<>();
        private final List<Listener> listeners = new ArrayList<>();

        public void add(String message) {
            String line = LocalTime.now().format(TIME) + "  " + message;
            lines.add(line);
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) {
                l.logged(line);
            }
        }

        public void addListener(Listener listener) {
            synchronized (listeners) {
                listeners.add(listener);
            }
        }

        public List<String> getLines() {
            return new ArrayList<>(lines);
        }
    }

    // ===================== FileCompare =====================
    /**
     * Compares a document with the registered original and describes WHAT is different.
     * Uses byte streams (Unit 3). The result is a list of plain sentences for the screen.
     */
    public static final class FileCompare {
        private File highlight;

        /** Picture of the uploaded file with the changed area boxed in red, or null for non-images. */
        public File getHighlight() {
            return highlight;
        }

        public List<String> compare(File original, File uploaded) throws IOException {
            List<String> notes = new ArrayList<>();
            notes.add("Size: original " + FileUtil.humanSize(original.length()) + ", uploaded "
                    + FileUtil.humanSize(uploaded.length()));
            long firstDiff = -1;
            long different = 0;
            long longest = Math.max(original.length(), uploaded.length());
            try (InputStream a = new BufferedInputStream(new FileInputStream(original));
                 InputStream b = new BufferedInputStream(new FileInputStream(uploaded))) {
                long position = 0;
                while (true) {
                    int x = a.read();
                    int y = b.read();
                    if (x == -1 && y == -1) {
                        break;
                    }
                    if (x != y) {
                        different++;
                        if (firstDiff < 0) {
                            firstDiff = position;
                        }
                    }
                    position++;
                }
            }
            if (firstDiff >= 0) {
                notes.add("First different byte at position " + firstDiff + "; " + different + " of " + longest
                        + " bytes differ");
            }
            if (FileUtil.isImage(original) && FileUtil.isImage(uploaded)) {
                compareImages(original, uploaded, notes);
            }
            return notes;
        }

        private void compareImages(File original, File uploaded, List<String> notes) throws IOException {
            BufferedImage a = ImageIO.read(original);
            BufferedImage b = ImageIO.read(uploaded);
            if (a == null || b == null) {
                return;
            }
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                notes.add("Picture size changed: " + a.getWidth() + "x" + a.getHeight() + " -> "
                        + b.getWidth() + "x" + b.getHeight() + " (the document was resized, cropped or redrawn)");
                return;
            }
            int minX = a.getWidth(), minY = a.getHeight(), maxX = -1, maxY = -1;
            long changed = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    if (a.getRGB(x, y) != b.getRGB(x, y)) {
                        changed++;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (changed == 0) {
                notes.add("The pictures look identical; only the file data (metadata or compression) was changed");
                return;
            }
            double percent = changed * 100.0 / ((double) a.getWidth() * a.getHeight());
            notes.add(String.format("%.2f%% of the picture was changed", percent));
            notes.add("Changed area: " + place(minX, maxX, minY, maxY, a.getWidth(), a.getHeight())
                    + " (x " + minX + "-" + maxX + ", y " + minY + "-" + maxY + ")");
            BufferedImage marked = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = marked.createGraphics();
            g.drawImage(b, 0, 0, null);
            g.setColor(new Color(220, 38, 38));
            g.setStroke(new BasicStroke(Math.max(4f, b.getWidth() / 250f)));
            int pad = 10;
            g.drawRect(minX - pad, minY - pad, (maxX - minX) + 2 * pad, (maxY - minY) + 2 * pad);
            g.dispose();
            highlight = File.createTempFile("certchain-changed-", ".png");
            highlight.deleteOnExit();
            ImageIO.write(marked, "png", highlight);
        }

        private static String place(int minX, int maxX, int minY, int maxY, int w, int h) {
            double cx = (minX + maxX) / 2.0 / w;
            double cy = (minY + maxY) / 2.0 / h;
            String vertical = cy < 0.33 ? "upper part" : cy < 0.66 ? "middle part" : "lower part";
            String horizontal = cx < 0.33 ? "left" : cx < 0.66 ? "centre" : "right";
            return vertical + ", " + horizontal;
        }
    }

    // ===================== FileUtil =====================
    /** File helpers built on byte streams (Unit 3: Input/Output, FileInputStream, OutputStream). */
    public static final class FileUtil {
        private FileUtil() {
        }

        /** Copies a file. Nested try blocks make sure both streams are closed. */
        public static void copy(File source, File target) throws IOException {
            InputStream in = new FileInputStream(source);
            try {
                OutputStream out = new FileOutputStream(target);
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        }

        public static void write(File target, byte[] data) throws IOException {
            OutputStream out = new FileOutputStream(target);
            try {
                out.write(data);
            } finally {
                out.close();
            }
        }

        public static String extension(String fileName) {
            int dot = fileName.lastIndexOf('.');
            return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        public static boolean isImage(File file) {
            String ext = extension(file.getName());
            return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("bmp");
        }

        public static String humanSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            }
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // ===================== GenericUtil =====================
    /** Generic methods and wildcard arguments (Unit 5). */
    public static final class GenericUtil {
        private GenericUtil() {
        }

        /**
         * Generic method with a bounded type parameter and a wildcard argument:
         * keeps only the items of one subclass, e.g. ofType(allData, CertificateData.class).
         */
        public static <T extends BlockData> List<T> ofType(Collection<? extends BlockData> source, Class<T> type) {
            List<T> result = new ArrayList<>();
            Iterator<? extends BlockData> it = source.iterator();
            while (it.hasNext()) {
                BlockData item = it.next();
                if (type.isInstance(item)) {
                    result.add(type.cast(item));
                }
            }
            return result;
        }

        /** Joins any collection of items with a separator. */
        public static <T> String join(Collection<? extends T> items, String separator) {
            StringBuilder sb = new StringBuilder();
            Iterator<? extends T> it = items.iterator();
            while (it.hasNext()) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(separator);
                }
            }
            return sb.toString();
        }
    }

    // ===================== Hasher =====================
    /**
     * SHA-256 helper. A final class with a private constructor: it cannot be extended or created (Unit 1: final).
     * sha256 is overloaded for String, byte[] and File (Unit 2: method overloading).
     */
    public static final class Hasher {
        private Hasher() {
        }

        public static String sha256(String text) {
            return sha256(text.getBytes(StandardCharsets.UTF_8));
        }

        public static String sha256(byte[] data) {
            return toHex(newDigest().digest(data));
        }

        /** Reads the file with a byte stream (FileInputStream) in 8 KB chunks. */
        public static String sha256(File file) throws IOException {
            MessageDigest md = newDigest();
            InputStream in = new FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            } finally {
                in.close();
            }
            return toHex(md.digest());
        }

        private static MessageDigest newDigest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is not available", e);
            }
        }

        public static String toHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        public static String shorten(String hash, int length) {
            return hash.length() <= length ? hash : hash.substring(0, length) + "...";
        }
    }

    // ===================== Ids =====================
    /** Random identifiers and secret keys. */
    public static final class Ids {
        private static final SecureRandom RANDOM = new SecureRandom();
        private static final String HEX = "0123456789ABCDEF";

        private Ids() {
        }

        /** Example: newId("CV-", 10) gives CV-3FA91C07DE. */
        public static String newId(String prefix, int length) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < length; i++) {
                sb.append(HEX.charAt(RANDOM.nextInt(HEX.length())));
            }
            return sb.toString();
        }

        /** A new secret key for an issuer (shown only once, stored only as a hash). */
        public static String newKey() {
            byte[] bytes = new byte[18];
            RANDOM.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }

    // ===================== Pair =====================
    /** Generic class with two type parameters (Unit 5). */
    public static class Pair<A, B> {
        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst() {
            return first;
        }

        public B getSecond() {
            return second;
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }

    // ===================== ResultHandler =====================
    /**
     * Generic interface (Unit 5). Background threads (the miner) call these methods when a job finishes,
     * so the caller never has to wait for it.
     */
    public interface ResultHandler<T> {
        void onSuccess(T result);

        void onError(Exception error);
    }

    // ===================== Account =====================
    /** A login account. Only a salted, repeatedly hashed password is stored, never the password itself. */
    public static class Account {
        private final String username;
        private final Role role;
        private final String salt;
        private final String passwordHash;
        private final String displayName;
        private final String issuerId;
        private final String created;
        private final String email;
        private final String phone;

        public Account(String username, Role role, String salt, String passwordHash, String displayName,
                       String issuerId, String created) {
            this(username, role, salt, passwordHash, displayName, issuerId, created, "", "");
        }

        public Account(String username, Role role, String salt, String passwordHash, String displayName,
                       String issuerId, String created, String email, String phone) {
            this.email = email == null ? "" : email;
            this.phone = phone == null ? "" : phone;
            this.username = username;
            this.role = role;
            this.salt = salt;
            this.passwordHash = passwordHash;
            this.displayName = displayName;
            this.issuerId = issuerId == null ? "" : issuerId;
            this.created = created;
        }

        public String getUsername() {
            return username;
        }

        public Role getRole() {
            return role;
        }

        public String getSalt() {
            return salt;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        /** Company name for a verifier, institution name for an issuer, "Authority" for the regulator. */
        public String getDisplayName() {
            return displayName;
        }

        /** Only issuer accounts have one. */
        public String getIssuerId() {
            return issuerId;
        }

        public String getCreated() {
            return created;
        }

        /** Used for OTP codes (sign-up and forgotten password). Empty for very old accounts. */
        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }

        public Account withPassword(String newSalt, String newHash) {
            return new Account(username, role, newSalt, newHash, displayName, issuerId, created, email, phone);
        }

        public Account withUsername(String newUsername) {
            return new Account(newUsername, role, salt, passwordHash, displayName, issuerId, created, email, phone);
        }

        public Account withContact(String newEmail, String newPhone) {
            return new Account(username, role, salt, passwordHash, displayName, issuerId, created, newEmail, newPhone);
        }

        /** a***@gmail.com - shown on screen instead of the full address. */
        public static String maskEmail(String email) {
            int at = email.indexOf('@');
            if (at < 2) {
                return email;
            }
            return email.charAt(0) + "***" + email.substring(at);
        }

        @Override
        public String toString() {
            return displayName + " (" + username + ")";
        }
    }

    // ===================== Block =====================
    /** One block of the chain. Implements the Hashable interface. */
    public static class Block implements Hashable {
        private final int index;
        private final long timestamp;
        private final BlockData data;
        private final String previousHash;
        private long nonce;
        private String hash;

        /** New block that still has to be mined. */
        public Block(int index, BlockData data, String previousHash) {
            this(index, System.currentTimeMillis(), data, previousHash, 0L, null);
        }

        /** Block loaded from the database (hash is the stored one, so tampering can be detected). */
        public Block(int index, long timestamp, BlockData data, String previousHash, long nonce, String hash) {
            this.index = index;
            this.timestamp = timestamp;
            this.data = data;
            this.previousHash = previousHash;
            this.nonce = nonce;
            this.hash = (hash == null) ? computeHash() : hash;
        }

        @Override
        public String computeHash() {
            return Hasher.sha256(index + "|" + timestamp + "|" + data.canonical() + "|" + previousHash + "|" + nonce);
        }

        /** Proof of work: change the nonce until the hash starts with enough zeros. */
        public void mine(int difficulty) {
            String prefix = "0".repeat(difficulty);
            hash = computeHash();
            while (!hash.startsWith(prefix)) {
                nonce++;
                hash = computeHash();
            }
        }

        public boolean hasValidHash() {
            return hash.equals(computeHash());
        }

        public int getIndex() {
            return index;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public BlockData getData() {
            return data;
        }

        public String getPreviousHash() {
            return previousHash;
        }

        public long getNonce() {
            return nonce;
        }

        public String getHash() {
            return hash;
        }
    }

    // ===================== BlockData =====================
    /**
     * Abstract class (Unit 1): the content stored inside a block.
     *
     * Hierarchy (multilevel inheritance):
     *   BlockData
     *     |- GenesisData
     *     |- CertificateData
     *     |     |- IssuerConfirmedCertificate
     *     |- RejectionData
     *
     * Subclasses override getType(), getTitle(), extraFields() and describe()  (Unit 2: runtime polymorphism).
     */
    public static abstract class BlockData {
        protected final String docHash;
        protected final String issuerId;
        protected final String issuerName;
        protected final String date;

        protected BlockData(String docHash, String issuerId, String issuerName, String date) {
            this.docHash = clean(docHash);
            this.issuerId = clean(issuerId);
            this.issuerName = clean(issuerName);
            this.date = clean(date);
        }

        /** Removes characters that would break the canonical text used for hashing. */
        protected static String clean(String text) {
            if (text == null) {
                return "";
            }
            return text.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
        }

        /** CERTIFICATE, CONFIRMED, REJECTION or GENESIS. */
        public abstract String getType();

        /** Short text shown in the ledger tree. */
        public abstract String getTitle();

        /** Fields of the subclass, joined with | for hashing. */
        protected abstract String extraFields();

        /** One-line description. Subclasses override and extend it. */
        public String describe() {
            return getType() + " recorded by " + issuerName + " on " + date;
        }

        public boolean isRejection() {
            return false;
        }

        /** final: no subclass may change how the canonical text is built, otherwise hashes could differ. */
        public final String canonical() {
            return getType() + "|" + docHash + "|" + issuerId + "|" + issuerName + "|" + date + "|" + extraFields();
        }

        public final String getDocHash() {
            return docHash;
        }

        public String getIssuerId() {
            return issuerId;
        }

        public String getIssuerName() {
            return issuerName;
        }

        public String getDate() {
            return date;
        }
    }

    // ===================== CertificateData =====================
    /** A certificate registered by an issuer. */
    public static class CertificateData extends BlockData {
        private final String certId;
        private final String holder;
        private final String course;
        private final String grade;
        private final String year;
        private final String certNumber;
        private final String source;

        /** Full constructor. */
        public CertificateData(String docHash, String issuerId, String issuerName, String date, String certId,
                               String holder, String course, String grade, String year, String certNumber,
                               String source) {
            super(docHash, issuerId, issuerName, date);
            this.certId = clean(certId);
            this.holder = clean(holder);
            this.course = clean(course);
            this.grade = clean(grade);
            this.year = clean(year);
            this.certNumber = clean(certNumber);
            this.source = clean(source);
        }

        /** Shorter constructor (Unit 2: constructor overloading) that delegates with this(...). */
        public CertificateData(String docHash, String issuerId, String issuerName, String date, String certId,
                               String holder, String course) {
            this(docHash, issuerId, issuerName, date, certId, holder, course, "", "", "", "generated");
        }

        @Override
        public String getType() {
            return "CERTIFICATE";
        }

        @Override
        public String getTitle() {
            return certId + "  -  " + holder;
        }

        @Override
        protected String extraFields() {
            return certId + "|" + holder + "|" + course + "|" + grade + "|" + year + "|" + certNumber + "|" + source;
        }

        @Override
        public String describe() {
            return "Certificate " + certId + " issued to " + holder + " by " + issuerName;
        }

        public String getCertId() {
            return certId;
        }

        public String getHolder() {
            return holder;
        }

        public String getCourse() {
            return course;
        }

        public String getGrade() {
            return grade;
        }

        public String getYear() {
            return year;
        }

        public String getCertNumber() {
            return certNumber;
        }

        public String getSource() {
            return source;
        }
    }

    // ===================== GenesisData =====================
    /** Content of block 0, the first block of every chain. */
    public static class GenesisData extends BlockData {
        public GenesisData(String date) {
            super("", "SYSTEM", "CertChain", date);
        }

        @Override
        public String getType() {
            return "GENESIS";
        }

        @Override
        public String getTitle() {
            return "Genesis block";
        }

        @Override
        protected String extraFields() {
            return "genesis";
        }

        @Override
        public String describe() {
            return "First block of the chain, created on " + date;
        }
    }

    // ===================== Hashable =====================
    /** Interface (Unit 1): anything that can produce its own SHA-256 fingerprint. */
    public interface Hashable {
        String computeHash();
    }

    // ===================== Signer =====================
    /**
     * Digital signatures with ECDSA (P-256, SHA-256). Every issuer owns a key pair. The private key is stored
     * only in encrypted form (AES-GCM, key derived from the account password with PBKDF2), so the database
     * alone is not enough to sign certificates.
     */
    public static final class Signer {
        private Signer() {
        }

        public static KeyPair generate() throws CertChainException {
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
                return g.generateKeyPair();
            } catch (GeneralSecurityException e) {
                throw new CertChainException("Cannot create signing keys: " + e.getMessage(), e);
            }
        }

        /** The text that is signed. It does not contain docHash, because a generated picture shows the signature. */
        public static String statement(BlockData d) {
            return d.getType() + "|" + d.getIssuerId() + "|" + d.getIssuerName() + "|" + d.getDate() + "|"
                    + d.extraFields();
        }

        public static String publicKeyText(KeyPair pair) {
            return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        }

        public static String fingerprint(String publicKeyText) {
            return Hasher.sha256(publicKeyText).substring(0, 16).toUpperCase();
        }

        private static SecretKeySpec passwordKey(String password, byte[] salt) throws GeneralSecurityException {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] k = f.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 65536, 256)).getEncoded();
            return new SecretKeySpec(k, "AES");
        }

        /** Returns salt:iv:cipher (Base64 parts) so the private key can be kept in the database. */
        public static String lock(PrivateKey key, String password) throws CertChainException {
            try {
                SecureRandom rnd = new SecureRandom();
                byte[] salt = new byte[16];
                byte[] iv = new byte[12];
                rnd.nextBytes(salt);
                rnd.nextBytes(iv);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, passwordKey(password, salt), new GCMParameterSpec(128, iv));
                byte[] ct = c.doFinal(key.getEncoded());
                Base64.Encoder b = Base64.getEncoder();
                return b.encodeToString(salt) + ":" + b.encodeToString(iv) + ":" + b.encodeToString(ct);
            } catch (GeneralSecurityException e) {
                throw new CertChainException("Cannot protect the signing key: " + e.getMessage(), e);
            }
        }

        public static PrivateKey unlock(String locked, String password) throws CertChainException {
            try {
                String[] p = locked.split(":");
                Base64.Decoder b = Base64.getDecoder();
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, passwordKey(password, b.decode(p[0])), new GCMParameterSpec(128, b.decode(p[1])));
                byte[] der = c.doFinal(b.decode(p[2]));
                return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
            } catch (GeneralSecurityException | RuntimeException e) {
                throw new CertChainException("The signing key could not be unlocked with this password", e);
            }
        }

        public static String sign(PrivateKey key, String text) throws CertChainException {
            try {
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initSign(key);
                s.update(text.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(s.sign());
            } catch (GeneralSecurityException e) {
                throw new CertChainException("Signing failed: " + e.getMessage(), e);
            }
        }

        public static boolean verify(String publicKeyText, String text, String signature) {
            try {
                PublicKey pub = KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyText)));
                Signature s = Signature.getInstance("SHA256withECDSA");
                s.initVerify(pub);
                s.update(text.getBytes(StandardCharsets.UTF_8));
                return s.verify(Base64.getDecoder().decode(signature));
            } catch (GeneralSecurityException | RuntimeException e) {
                return false;
            }
        }
    }

    // ===================== Issuer =====================
    /** An institution approved by the authority. Only a hash of its key is stored. */
    public static class Issuer {
        private final String id;
        private final String name;
        private final String keyHash;
        private final String created;

        public Issuer(String id, String name, String keyHash, String created) {
            this.id = id;
            this.name = name;
            this.keyHash = keyHash;
            this.created = created;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getKeyHash() {
            return keyHash;
        }

        public String getCreated() {
            return created;
        }

        /** Used by ComboBox and ListView to display the issuer. */
        @Override
        public String toString() {
            return name;
        }
    }

    // ===================== IssuerConfirmedCertificate =====================
    /**
     * A certificate the issuer confirmed after a company asked about it.
     * Third level of the hierarchy: BlockData -> CertificateData -> IssuerConfirmedCertificate.
     */
    public static class IssuerConfirmedCertificate extends CertificateData {
        private final String requestId;
        private final String remarks;

        public IssuerConfirmedCertificate(String docHash, String issuerId, String issuerName, String date,
                                          String certId, String holder, String course, String grade, String year,
                                          String certNumber, String requestId, String remarks) {
            super(docHash, issuerId, issuerName, date, certId, holder, course, grade, year, certNumber,
                    "issuer-confirmed");
            this.requestId = clean(requestId);
            this.remarks = clean(remarks);
        }

        @Override
        public String getType() {
            return "CONFIRMED";
        }

        @Override
        protected String extraFields() {
            return super.extraFields() + "|" + requestId + "|" + remarks;
        }

        @Override
        public String describe() {
            return super.describe() + " (confirmed on request " + requestId + ")";
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRemarks() {
            return remarks;
        }
    }

    // ===================== RejectionData =====================
    /** The issuer says a document is NOT genuine. Stored permanently so every company sees the warning. */
    public static class RejectionData extends BlockData {
        private final String holder;
        private final String requestId;
        private final String remarks;

        public RejectionData(String docHash, String issuerId, String issuerName, String date, String holder,
                             String requestId, String remarks) {
            super(docHash, issuerId, issuerName, date);
            this.holder = clean(holder);
            this.requestId = clean(requestId);
            this.remarks = clean(remarks);
        }

        @Override
        public String getType() {
            return "REJECTION";
        }

        @Override
        public String getTitle() {
            return "REJECTED  -  " + holder;
        }

        @Override
        protected String extraFields() {
            return holder + "|" + requestId + "|" + remarks;
        }

        @Override
        public String describe() {
            return "Document claimed by " + holder + " was rejected by " + issuerName + ": " + remarks;
        }

        @Override
        public boolean isRejection() {
            return true;
        }

        public String getHolder() {
            return holder;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRemarks() {
            return remarks;
        }
    }

    // ===================== RequestStatus =====================
    /** Life cycle of a verification request. */
    public enum RequestStatus {
        PENDING("Pending"),
        APPROVED("Confirmed genuine"),
        REJECTED("Marked fake");

        private final String label;

        RequestStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ===================== Role =====================
    /** The three kinds of user. Each one has its own portal. */
    public enum Role {
        AUTHORITY("Authority"),
        ISSUER("Issuer"),
        VERIFIER("Verifier");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ===================== ValidationReport =====================
    /** Result of checking the whole chain. */
    public static class ValidationReport {
        private final boolean valid;
        private final String message;
        private final int badBlockIndex;

        public ValidationReport(boolean valid, String message, int badBlockIndex) {
            this.valid = valid;
            this.message = message;
            this.badBlockIndex = badBlockIndex;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public int getBadBlockIndex() {
            return badBlockIndex;
        }
    }

    // ===================== Verdict =====================
    /** Possible answers when a company verifies a document. */
    public enum Verdict {
        AUTHENTIC("AUTHENTIC"),
        FORGED("FORGED"),
        PENDING("PENDING REVIEW"),
        NOT_VERIFIED("NOT VERIFIED"),
        MODIFIED("MODIFIED"),
        ERROR("ERROR");

        private final String label;

        Verdict(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ===================== VerificationRequest =====================
    /** A company asks an issuer to confirm one document. */
    public static class VerificationRequest {
        private final String id;
        private final String issuerId;
        private final String company;
        private final String candidate;
        private final String certNumber;
        private final String course;
        private final String year;
        private final String docHash;
        private final String fileName;
        private final String storedAs;
        private final String created;
        private RequestStatus status;
        private String remarks;
        private String decidedOn;
        private String certId;

        public VerificationRequest(String id, String issuerId, String company, String candidate, String certNumber,
                                   String course, String year, String docHash, String fileName, String storedAs,
                                   RequestStatus status, String remarks, String created, String decidedOn,
                                   String certId) {
            this.id = id;
            this.issuerId = issuerId;
            this.company = company;
            this.candidate = candidate;
            this.certNumber = certNumber;
            this.course = course;
            this.year = year;
            this.docHash = docHash;
            this.fileName = fileName;
            this.storedAs = storedAs;
            this.status = status;
            this.remarks = remarks;
            this.created = created;
            this.decidedOn = decidedOn;
            this.certId = certId;
        }

        public String getId() {
            return id;
        }

        public String getIssuerId() {
            return issuerId;
        }

        public String getCompany() {
            return company;
        }

        public String getCandidate() {
            return candidate;
        }

        public String getCertNumber() {
            return certNumber;
        }

        public String getCourse() {
            return course;
        }

        public String getYear() {
            return year;
        }

        public String getDocHash() {
            return docHash;
        }

        public String getFileName() {
            return fileName;
        }

        public String getStoredAs() {
            return storedAs;
        }

        public String getCreated() {
            return created;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public void setStatus(RequestStatus status) {
            this.status = status;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getDecidedOn() {
            return decidedOn;
        }

        public void setDecidedOn(String decidedOn) {
            this.decidedOn = decidedOn;
        }

        public String getCertId() {
            return certId;
        }

        public void setCertId(String certId) {
            this.certId = certId;
        }
    }

    // ===================== VerificationResult =====================
    /** Everything the screen needs to show after a verification. */
    public static class VerificationResult {
        private final Verdict verdict;
        private final String message;
        private final String docHash;
        private BlockData data;
        private int blockIndex = -1;
        private String blockHash = "";
        private String requestId = "";
        private Boolean fileIntact;
        private String originalHash = "";
        private String certId = "";
        private java.util.List<String> changeNotes = new java.util.ArrayList<>();
        private java.io.File highlightFile;
        private String signatureState = "";
        private String signatureInfo = "";

        public VerificationResult withSignature(String state, String info) {
            this.signatureState = state;
            this.signatureInfo = info;
            return this;
        }

        /** VALID, INVALID, UNSIGNED or empty (no record found). */
        public String getSignatureState() {
            return signatureState;
        }

        public String getSignatureInfo() {
            return signatureInfo;
        }

        public VerificationResult withModification(String certId, String originalHash, java.util.List<String> notes, java.io.File highlight) {
            this.certId = certId;
            this.originalHash = originalHash;
            this.changeNotes = notes;
            this.highlightFile = highlight;
            return this;
        }

        public String getOriginalHash() {
            return originalHash;
        }

        public String getCertId() {
            return certId;
        }

        public java.util.List<String> getChangeNotes() {
            return changeNotes;
        }

        public java.io.File getHighlightFile() {
            return highlightFile;
        }

        public VerificationResult(Verdict verdict, String message, String docHash) {
            this.verdict = verdict;
            this.message = message;
            this.docHash = docHash;
        }

        public VerificationResult withBlock(Block block) {
            this.data = block.getData();
            this.blockIndex = block.getIndex();
            this.blockHash = block.getHash();
            return this;
        }

        public VerificationResult withRequestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public VerificationResult withFileIntact(Boolean fileIntact) {
            this.fileIntact = fileIntact;
            return this;
        }

        public Verdict getVerdict() {
            return verdict;
        }

        public String getMessage() {
            return message;
        }

        public String getDocHash() {
            return docHash;
        }

        public BlockData getData() {
            return data;
        }

        public int getBlockIndex() {
            return blockIndex;
        }

        public String getBlockHash() {
            return blockHash;
        }

        public String getRequestId() {
            return requestId;
        }

        public Boolean getFileIntact() {
            return fileIntact;
        }
    }

}
