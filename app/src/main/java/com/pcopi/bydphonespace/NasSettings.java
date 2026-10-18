package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** NAS credentials are encrypted with a device-bound Android Keystore key. */
final class NasSettings {
    static final String AUTO="AUTO", LAN="LAN", TAIL="TAILSCALE";
    private static final String PREF="nas_connection_v2", ALIAS="BydNasConnectionAesV1";
    final String lan, tail, user, pass, mode;

    NasSettings(String l,String t,String u,String p,String m){
        lan=l==null?"":l.trim();tail=t==null?"":t.trim();
        user=u==null?"":u.trim();pass=p==null?"":p;
        mode=LAN.equals(m)||TAIL.equals(m)?m:AUTO;
    }

    static NasSettings load(Context ctx){
        SharedPreferences prefs=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        SharedPreferences legacy=ctx.getSharedPreferences("nas",Context.MODE_PRIVATE);
        String lan=prefs.getString("lan",legacy.getString("lan",""));
        String tail=prefs.getString("tail",legacy.getString("tail",""));
        String user=prefs.getString("user",legacy.getString("user",""));
        String password="";
        try{
            String b=prefs.getString("password","");
            String iv=prefs.getString("iv","");
            if(!b.isEmpty()&&!iv.isEmpty()){
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
                password=new String(cipher.doFinal(Base64.decode(b,Base64.NO_WRAP)),StandardCharsets.UTF_8);
            }
        }catch(Exception ignored){} // A missing/invalid keystore key must not expose credentials.
        return new NasSettings(lan,tail,user,password,prefs.getString("mode",AUTO));
    }

    void save(Context ctx) throws Exception{
        SharedPreferences.Editor edit=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        edit.putString("lan",lan).putString("tail",tail).putString("user",user).putString("mode",mode);
        if(pass.isEmpty()){
            edit.remove("password").remove("iv");
        }else{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key());
            edit.putString("password",Base64.encodeToString(cipher.doFinal(pass.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
            edit.putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        }
        if(!edit.commit())throw new IllegalStateException("ไม่สามารถบันทึกการเชื่อมต่อได้");
    }

    private static SecretKey key() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            gen.generateKey();
        }
        KeyStore.SecretKeyEntry entry=(KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null);
        if(entry==null)throw new IllegalStateException("ไม่พบ Android Keystore");
        return entry.getSecretKey();
    }
}
