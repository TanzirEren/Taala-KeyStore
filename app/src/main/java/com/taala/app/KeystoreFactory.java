package com.taala.app;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates the keystore and every file the web app produced: JKS, BKS, PKCS12, PEMs, key.txt and the zip. */
public final class KeystoreFactory {
    public interface Progress { void step(int n); }

    private static final Provider BC = new BouncyCastleProvider();
    private static final SecureRandom RND = new SecureRandom();

    private KeystoreFactory() {}

    // ---------------------------------------------------------------- create

    public static Project create(Project p, Progress pr) throws Exception {
        pr.step(0);
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(p.bits, RND);
        KeyPair kp = g.generateKeyPair();

        pr.step(1);
        X500NameBuilder nb = new X500NameBuilder(BCStyle.INSTANCE);
        boolean any = false;
        any |= add(nb, BCStyle.CN, p.cn);
        any |= add(nb, BCStyle.OU, p.ou);
        any |= add(nb, BCStyle.O, p.o);
        any |= add(nb, BCStyle.L, p.l);
        any |= add(nb, BCStyle.ST, p.st);
        any |= add(nb, BCStyle.C, p.c == null ? "" : p.c.toUpperCase(Locale.ROOT));
        if (!any) add(nb, BCStyle.CN, p.app);
        X500Name name = nb.build();
        Calendar cal = Calendar.getInstance();
        Date from = cal.getTime();
        cal.add(Calendar.YEAR, p.years);
        Date to = cal.getTime();
        BigInteger serial = new BigInteger(63, RND).add(BigInteger.ONE);
        JcaX509v3CertificateBuilder cb = new JcaX509v3CertificateBuilder(name, serial, from, to, name, kp.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(cb.build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));
        byte[] der = cert.getEncoded();
        byte[] pk8 = kp.getPrivate().getEncoded();

        pr.step(2);
        p.p12 = enc(storeBytes("PKCS12-3DES-3DES", p.alias, kp.getPrivate(), cert, p.sp, p.sp));

        pr.step(3);
        p.jks = enc(jks(pk8, der, p.alias, p.sp, p.kp));
        p.cert = pem("CERTIFICATE", der);
        p.key = pem("PRIVATE KEY", pk8);
        p.sha1 = fp("SHA-1", der);
        p.sha256 = fp("SHA-256", der);
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        p.expires = day.format(to);
        p.ok = verify(p);
        p.created = System.currentTimeMillis();
        return p;
    }

    private static boolean add(X500NameBuilder nb, ASN1ObjectIdentifier oid, String v) {
        if (v == null || v.trim().isEmpty()) return false;
        nb.addRDN(oid, v.trim());
        return true;
    }

    // ---------------------------------------------------------------- keystores

    private static byte[] storeBytes(String type, String alias, PrivateKey key, java.security.cert.Certificate cert,
                                     String storePw, String keyPw) throws Exception {
        KeyStore ks = KeyStore.getInstance(type, BC);
        ks.load(null, null);
        ks.setKeyEntry(alias, key, keyPw.toCharArray(), new java.security.cert.Certificate[]{cert});
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ks.store(o, storePw.toCharArray());
        return o.toByteArray();
    }

    /** BKS is the keystore type Android itself understands (Android has no JKS support). */
    static byte[] bksBytes(Project p) throws Exception {
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(unpem(p.key)));
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(unpem(p.cert)));
        return storeBytes("BKS", p.alias, key, cert, p.sp, p.kp);
    }

    /** Writes a Sun JKS file (magic FEEDFEED, version 2) with the proprietary key protector. */
    static byte[] jks(byte[] pkcs8, byte[] cert, String alias, String sp, String kp) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        byte[] kb = utf16be(kp), salt = new byte[20];
        RND.nextBytes(salt);
        byte[] stream = new byte[pkcs8.length];
        byte[] d = salt;
        for (int off = 0; off < stream.length; off += 20) {
            sha.reset();
            sha.update(kb);
            sha.update(d);
            d = sha.digest();
            System.arraycopy(d, 0, stream, off, Math.min(20, stream.length - off));
        }
        byte[] x = new byte[pkcs8.length];
        for (int i = 0; i < x.length; i++) x[i] = (byte) (pkcs8[i] ^ stream[i]);
        sha.reset();
        sha.update(kb);
        sha.update(pkcs8);
        byte[] check = sha.digest();
        ByteArrayOutputStream enc = new ByteArrayOutputStream();
        enc.write(salt);
        enc.write(x);
        enc.write(check);
        byte[] epki = new EncryptedPrivateKeyInfo(
                new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.3.6.1.4.1.42.2.17.1.1"), DERNull.INSTANCE),
                enc.toByteArray()).getEncoded();

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        o.writeInt(0xFEEDFEED);
        o.writeInt(2);
        o.writeInt(1);
        o.writeInt(1);
        o.writeUTF(alias.toLowerCase(Locale.ROOT));
        o.writeLong(System.currentTimeMillis());
        o.writeInt(epki.length);
        o.write(epki);
        o.writeInt(1);
        o.writeUTF("X.509");
        o.writeInt(cert.length);
        o.write(cert);
        byte[] body = bo.toByteArray();
        sha.reset();
        sha.update(utf16be(sp));
        sha.update("Mighty Aphrodite".getBytes(StandardCharsets.US_ASCII));
        sha.update(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(body);
        out.write(sha.digest());
        return out.toByteArray();
    }

    private static boolean verify(Project p) {
        try {
            KeyStore k1 = KeyStore.getInstance("PKCS12", BC);
            k1.load(new ByteArrayInputStream(dec(p.p12)), p.sp.toCharArray());
            String a1 = k1.aliases().nextElement();
            Key key1 = k1.getKey(a1, p.sp.toCharArray());
            if (key1 == null || !fp("SHA-1", k1.getCertificate(a1).getEncoded()).equals(p.sha1)) return false;
            KeyStore k2 = KeyStore.getInstance("BKS", BC);
            k2.load(new ByteArrayInputStream(bksBytes(p)), p.sp.toCharArray());
            Key key2 = k2.getKey(p.alias, p.kp.toCharArray());
            return key2 != null && fp("SHA-1", k2.getCertificate(p.alias).getEncoded()).equals(p.sha1);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- files

    public static LinkedHashMap<String, byte[]> files(Project p) throws Exception {
        LinkedHashMap<String, byte[]> m = new LinkedHashMap<>();
        m.put("keystore.jks", dec(p.jks));
        m.put("keystore.bks", bksBytes(p));
        m.put("keystore.p12", dec(p.p12));
        m.put("certificate.pem", p.cert.getBytes(StandardCharsets.UTF_8));
        m.put("private-key.pem", p.key.getBytes(StandardCharsets.UTF_8));
        m.put("keystore_base64.txt", p.jks.getBytes(StandardCharsets.UTF_8));
        m.put("key.txt", keyTxt(p).getBytes(StandardCharsets.UTF_8));
        return m;
    }

    public static byte[] zip(Project p) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (java.util.Map.Entry<String, byte[]> e : files(p).entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    public static String keyTxt(Project p) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dash = "-";
        return "TAALA KEYSTORE – " + p.project + "\n"
                + "Created: " + iso.format(new Date(p.created)) + "\n"
                + "Valid until: " + p.expires + "\n\n"
                + "=== GitHub Secrets ===\n"
                + "KEYSTORE_BASE64=" + p.jks + "\n"
                + "KEYSTORE_PASSWORD=" + p.sp + "\n"
                + "KEY_ALIAS=" + p.alias + "\n"
                + "KEY_PASSWORD=" + p.kp + "\n\n"
                + "=== Google Console fingerprints ===\n"
                + "SHA-1:   " + p.sha1 + "\n"
                + "SHA-256: " + p.sha256 + "\n\n"
                + "=== Project details ===\n"
                + "Project name: " + p.project + "\n"
                + "App name: " + p.app + "\n"
                + "Package name: " + (p.pkg.isEmpty() ? dash : p.pkg) + "\n"
                + "Key: RSA " + p.bits + ", " + p.years + " years\n"
                + "CN=" + or(p.cn) + ", OU=" + or(p.ou) + ", O=" + or(p.o) + ", L=" + or(p.l)
                + ", ST=" + or(p.st) + ", C=" + or(p.c) + "\n\n"
                + "Files:\n"
                + "keystore.bks - for Android apps (Android has no JKS support; this is its native type). Alias and passwords above.\n"
                + "keystore.jks - for Gradle, Android Studio, apksigner and keytool on a computer. Android apps cannot open it (error: JKS not found).\n"
                + "keystore.p12 - PKCS12, also readable on Android. Key password must equal the keystore password: " + p.sp + "\n"
                + "certificate.pem, private-key.pem - public certificate and PKCS8 private key\n"
                + "Keep this file private. Anyone holding it can sign apps as you.\n";
    }

    private static String or(String s) { return s == null || s.isEmpty() ? "-" : s; }

    // ---------------------------------------------------------------- inspect

    /** Reads a .p12 / .pfx and returns label and value rows. */
    public static List<String[]> inspect(byte[] data, String pw) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12", BC);
        ks.load(new ByteArrayInputStream(data), pw.toCharArray());
        String alias = null;
        X509Certificate c = null;
        Enumeration<String> en = ks.aliases();
        while (en.hasMoreElements()) {
            String a = en.nextElement();
            java.security.cert.Certificate ce = ks.getCertificate(a);
            if (ce instanceof X509Certificate) { alias = a; c = (X509Certificate) ce; break; }
        }
        if (c == null) throw new Exception("no certificate inside");
        byte[] der = c.getEncoded();
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Alias", alias});
        rows.add(new String[]{"Subject", c.getSubjectX500Principal().getName()});
        rows.add(new String[]{"SHA-1", fp("SHA-1", der)});
        rows.add(new String[]{"SHA-256", fp("SHA-256", der)});
        rows.add(new String[]{"Valid until", day.format(c.getNotAfter())});
        return rows;
    }

    // ---------------------------------------------------------------- helpers

    static String fp(String alg, byte[] der) throws Exception {
        byte[] h = MessageDigest.getInstance(alg).digest(der);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.ROOT, "%02X", h[i] & 255));
        }
        return sb.toString();
    }

    static String enc(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    static byte[] dec(String s) { return Base64.getDecoder().decode(s); }

    static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }

    static byte[] unpem(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").trim());
    }

    private static byte[] utf16be(String s) {
        byte[] b = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) {
            b[2 * i] = (byte) (s.charAt(i) >> 8);
            b[2 * i + 1] = (byte) s.charAt(i);
        }
        return b;
    }
}
