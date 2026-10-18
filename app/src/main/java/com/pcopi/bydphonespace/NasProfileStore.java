package com.pcopi.bydphonespace;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Multiple encrypted NAS profiles. Credentials are never displayed on the account picker. */
final class NasProfileStore {
    static final class Profile {
        final String id, label;
        final NasSettings settings;
        Profile(String id, String label, NasSettings settings){
            this.id=id;this.label=label;this.settings=settings;
        }
    }
    private static final String PREF="nas_profiles_secure_v1";
    private static final String ALIAS="BydNasProfilesAesV1";
    private static final String KEY_BLOB="profiles", KEY_IV="iv", KEY_MIGRATED="migrated";
    private static final String KEY_LAST="last_profile";
    private NasProfileStore(){}

    static List<Profile> load(Context context){
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        List<Profile> out=new ArrayList<>();
        String blob=p.getString(KEY_BLOB,"");
        String iv=p.getString(KEY_IV,"");
        if(!blob.isEmpty()&&!iv.isEmpty()){
            try{
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
                String json=new String(cipher.doFinal(Base64.decode(blob,Base64.NO_WRAP)),StandardCharsets.UTF_8);
                JSONArray array=new JSONArray(json);
                for(int i=0;i<array.length();i++){
                    JSONObject x=array.getJSONObject(i);
                    NasSettings settings=new NasSettings(x.optString("lan"),x.optString("tail"),
                        x.optString("user"),x.optString("pass"),x.optString("mode",NasSettings.AUTO));
                    String id=x.optString("id");
                    if(!id.isEmpty())out.add(new Profile(id,x.optString("label","NAS"),settings));
                }
                return out;
            }catch(Exception e){
                // Do not silently reset secrets or overwrite an unreadable encrypted store.
                throw new IllegalStateException("อ่านโปรไฟล์ NAS ที่เข้ารหัสไม่ได้ กรุณาอย่าลบข้อมูล: "+e.getClass().getSimpleName(),e);
            }
        }
        if(!p.getBoolean(KEY_MIGRATED,false)){
            NasSettings old=NasSettings.load(context);
            if(!old.user.isEmpty()&&!old.pass.isEmpty()){
                out.add(new Profile("p-legacy","บัญชี NAS เดิม",old));
            }
            try{
                save(context,out);
                p.edit().putBoolean(KEY_MIGRATED,true).commit();
            }catch(Exception e){throw new IllegalStateException("ย้ายค่าบัญชี NAS เดิมไม่สำเร็จ: "+e.getMessage(),e);}
        }
        return out;
    }

    static void save(Context context,List<Profile> profiles)throws Exception{
        JSONArray data=new JSONArray();
        for(Profile profile:profiles){
            JSONObject x=new JSONObject();
            x.put("id",profile.id);
            x.put("label",profile.label);
            x.put("lan",profile.settings.lan);
            x.put("tail",profile.settings.tail);
            x.put("user",profile.settings.user);
            x.put("pass",profile.settings.pass);
            x.put("mode",profile.settings.mode);
            data.put(x);
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor edit=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        edit.putString(KEY_BLOB,Base64.encodeToString(encrypted,Base64.NO_WRAP));
        edit.putString(KEY_IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        edit.putBoolean(KEY_MIGRATED,true);
        if(!edit.commit())throw new IllegalStateException("เขียนโปรไฟล์ลงเครื่องไม่สำเร็จ");
    }

    static String last(Context context){
        return context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY_LAST,"");
    }
    static void setLast(Context context,String id){
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_LAST,id).apply();
    }
    static String newId(){return "nas-"+System.currentTimeMillis()+"-"+java.util.UUID.randomUUID().toString().substring(0,8);}

    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        KeyStore.SecretKeyEntry entry=(KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null);
        if(entry==null)throw new IllegalStateException("ไม่พบกุญแจเข้ารหัส Android Keystore");
        return entry.getSecretKey();
    }
}
