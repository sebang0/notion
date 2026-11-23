package com.sebang.notion;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/* ══════════════════════════════════════════════════════════════════════
   [패턴/생체 빠른 잠금 해제 저장소]
   - 데이터 비밀번호를 휴대폰 하드웨어 키저장소(Android Keystore) AES-256-GCM 키로 암호화해
     앱 전용 저장공간(SharedPreferences)에 보관 → 서버·GitHub로 보내지 않음
   - 패턴: 원문은 저장하지 않고 PBKDF2-SHA256(무작위 salt) 해시만 보관 → 일치할 때만 비밀번호 반환
   - 실패 제한: 패턴 연속 5회 실패 → 패턴·생체 설정과 보관 비밀번호를 모두 삭제
   - 패턴·생체가 모두 꺼지면 보관 비밀번호 삭제
   - 판정은 앱(네이티브)에서 수행 → 웹 화면 쪽 조작으로 실패 횟수 우회 불가
   ══════════════════════════════════════════════════════════════════════ */
final class QuickLock {

    static final int MAX_FAILS   = 5;   // 연속 실패 허용 횟수
    static final int MIN_POINTS  = 4;   // 패턴 최소 점 개수

    private static final String KS_ALIAS  = "sebang_quick_v1";
    private static final String PREFS     = "quick_lock";
    private static final int    PBKDF_ITER = 20000;

    private final SharedPreferences sp;

    QuickLock(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ── [1] 상태 조회 ── */
    boolean hasPassword() { return sp.contains("pw_ct"); }
    boolean patternOn()   { return sp.getBoolean("pattern_on", false) && hasPassword(); }
    boolean bioOn()       { return sp.getBoolean("bio_on", false) && hasPassword(); }
    int     fails()       { return sp.getInt("fails", 0); }

    /* ── [2] 패턴 등록: 비밀번호 보관 + 패턴 해시 저장 · 실패 횟수 초기화 ── */
    void setPattern(String pw, String pattern) throws Exception {
        if (pointCount(pattern) < MIN_POINTS) throw new IllegalArgumentException("점 " + MIN_POINTS + "개 이상 연결해 주세요.");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        storePassword(pw);
        sp.edit()
          .putString("pat_salt", b64(salt))
          .putString("pat_hash", b64(hashPattern(pattern, salt)))
          .putBoolean("pattern_on", true)
          .putInt("fails", 0)
          .apply();
    }

    /* ── [3] 생체 켜기/끄기 (켤 때 비밀번호 보관) ── */
    void setBio(String pw, boolean on) throws Exception {
        if (on) { storePassword(pw); sp.edit().putBoolean("bio_on", true).apply(); }
        else    { sp.edit().putBoolean("bio_on", false).apply(); wipeIfUnused(); }
    }

    /* ── [4] 패턴 끄기 ── */
    void disablePattern() {
        sp.edit().putBoolean("pattern_on", false).remove("pat_salt").remove("pat_hash").putInt("fails", 0).apply();
        wipeIfUnused();
    }

    /* ── [5] 비밀번호 갱신: 비밀번호로 열었거나 변경했을 때 보관본을 최신으로 교체 ── */
    void updatePassword(String pw) throws Exception {
        if (hasPassword()) storePassword(pw);
    }

    /* ── [6] 패턴 확인: 맞으면 비밀번호 반환 · 틀리면 null (5회째 전체 삭제) ── */
    String unlockPattern(String pattern) throws Exception {
        if (!patternOn()) return null;
        byte[] salt = unb64(sp.getString("pat_salt", ""));
        byte[] want = unb64(sp.getString("pat_hash", ""));
        if (MessageDigest.isEqual(want, hashPattern(pattern, salt))) {
            sp.edit().putInt("fails", 0).apply();
            return loadPassword();
        }
        int f = fails() + 1;
        if (f >= MAX_FAILS) clearAll();
        else sp.edit().putInt("fails", f).apply();
        return null;
    }

    /* ── [7] 생체 인증 성공 후 비밀번호 반환 ── */
    String unlockBio() throws Exception {
        return bioOn() ? loadPassword() : null;
    }

    /* ── [8] 전체 삭제 (5회 실패 · 키 손상) ── */
    void clearAll() {
        sp.edit().clear().apply();
        try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(KS_ALIAS); }
        catch (Exception ignored) { /* 키가 없으면 무시 */ }
    }

    /* ━━━━━━━━━━━━━━━━━━━━ 내부 도구 ━━━━━━━━━━━━━━━━━━━━ */

    /** 패턴·생체 모두 꺼지면 보관 비밀번호 삭제 */
    private void wipeIfUnused() {
        if (!sp.getBoolean("pattern_on", false) && !sp.getBoolean("bio_on", false)) clearAll();
    }

    /** 키저장소 AES 키 (없으면 생성 · 키 원본은 휴대폰 밖으로 꺼낼 수 없음) */
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KS_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KS_ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private void storePassword(String pw) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = c.doFinal(pw.getBytes(StandardCharsets.UTF_8));
        sp.edit().putString("pw_iv", b64(c.getIV())).putString("pw_ct", b64(ct)).apply();
    }

    private String loadPassword() throws Exception {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, unb64(sp.getString("pw_iv", ""))));
            return new String(c.doFinal(unb64(sp.getString("pw_ct", ""))), StandardCharsets.UTF_8);
        } catch (Exception e) {
            clearAll();   // 키 손상(기기 보안 초기화 등) → 다시 등록하도록 전체 삭제
            throw e;
        }
    }

    private static byte[] hashPattern(String pattern, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pattern.toCharArray(), salt, PBKDF_ITER, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private static int pointCount(String pattern) {
        return pattern == null || pattern.isEmpty() ? 0 : pattern.split("-").length;
    }

    private static String b64(byte[] b)   { return Base64.encodeToString(b, Base64.NO_WRAP); }
    private static byte[] unb64(String s) { return Base64.decode(s, Base64.NO_WRAP); }
}
