package com.example.biometricapptry;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "BIOMETRICS";
    private static String KEY_NAME = "KEY_NAME";
    private Executor executor;
    private EditText user;
    private EditText password;
    private Button biometricLoginButton;
    private Button normalLoginButton;
    private AlertDialog dialogHuella;
    private BiometricPrompt biometricPromptDoLogin;
    private BiometricPrompt biometricPromptRegisterLogin;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean registerUser = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        user = findViewById(R.id.user_edit);
        password = findViewById(R.id.password_edit);
        normalLoginButton = findViewById(R.id.btn_login_normal);
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate()){
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Log.e(TAG,"BIOMETRIC_ERROR_HW_UNAVAILABLE");
                break;
            case BiometricManager.BIOMETRIC_SUCCESS:
                Log.e(TAG,"BIOMETRIC_SUCCESS");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                Log.e(TAG,"BIOMETRIC_ERROR_NONE_ENROLLED");
                break;
            default:
                break;
        }

        executor = ContextCompat.getMainExecutor(this);
        biometricPromptDoLogin = getBiometricPromptDoLogin();
        biometricPromptRegisterLogin = getBiometricPromptRegisterLogin();
        try {
            generateSecretKey(generateSecretKeyOnlyBiometrics());
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login for my app")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use account password")
                .build();

        // Prompt appears when user clicks "Log in".
        // Consider integrating with the keystore to unlock cryptographic operations,
        // if needed by your app.
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                registerUser = true;
                dialogInterface.dismiss();
                callAuthenticateWithOnlyBiometrics(promptInfo, false);
            }
        });
        builder.setMessage("¿Desea activar la huella?")
                .setTitle("Activación de huella");
        dialogHuella = builder.create();
        biometricLoginButton = findViewById(R.id.btn_login);
        biometricLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = getBaseContext().getSharedPreferences("USER",Context.MODE_PRIVATE);
                String user = preferences.getString("USER","");
                String password = preferences.getString("PASSWORD","");
                if(!user.isEmpty() && !password.isEmpty()){
                    callAuthenticateWithOnlyBiometrics(promptInfo, true);
                }else{
                    dialogHuella.show();
                }

            }
        });
        normalLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences preferences = getBaseContext().getSharedPreferences("USER", Context.MODE_PRIVATE);
                if(preferences.getString("USER","").isEmpty() && preferences.getString("PASSWORD","").isEmpty()){
                    Log.e(TAG,"NO REGISTRO HUELLA");
                    dialogHuella.show();
                }else{
                    doLogin(user.toString(), password.toString());
                }
            }
        });

    }

    private void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(keyGenParameterSpec);
        keyGenerator.generateKey();
    }

    private SecretKey getSecretKey() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return ((SecretKey)keyStore.getKey(KEY_NAME, null));
    }


    private Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    private void callAuthenticateWithOnlyBiometrics(BiometricPrompt.PromptInfo promptInfo, boolean type){
        Cipher cipher = null;
        try {
            cipher = getCipher();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        SecretKey secretKey = null;
        try {
            secretKey = getSecretKey();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        if(type){
            biometricPromptDoLogin.authenticate(promptInfo,
                    new BiometricPrompt.CryptoObject(cipher));
        }else{
            biometricPromptRegisterLogin.authenticate(promptInfo,
                    new BiometricPrompt.CryptoObject(cipher));
        }
    }

    private KeyGenParameterSpec generateSecretKeyOnlyBiometrics(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    // Invalidate the keys if the user has registered a new biometric
                    // credential, such as a new fingerprint. Can call this method only
                    // on Android 7.0 (API level 24) or higher. The variable
                    // "invalidatedByBiometricEnrollment" is true by default.
                    .setInvalidatedByBiometricEnrollment(true)
                    .build();
        }else{
            return null;
        }
    }

    private boolean doLogin(String user, String password){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.setMessage("Usuario: "+user+ " contraseña "+password)
                .setTitle("Inicio");
        AlertDialog dialog = builder.create();
        dialog.show();
        return false;
    }

    public BiometricPrompt getBiometricPromptDoLogin(){
        return new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(),
                        "Authentication succeeded!", Toast.LENGTH_SHORT).show();
                SharedPreferences preferences = getBaseContext().getSharedPreferences("USER", Context.MODE_PRIVATE);
                String userSaved = preferences.getString("USER","");
                String passSaved = preferences.getString("PASSWORD","");
                Log.e(TAG,"Haciendo Login con info guardada: "+userSaved + " , "+passSaved);
                if(!userSaved.isEmpty() && !passSaved.isEmpty()){
                    doLogin(userSaved, passSaved);
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    public BiometricPrompt getBiometricPromptRegisterLogin(){
        return new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(getApplicationContext(),
                        "Authentication succeeded!", Toast.LENGTH_SHORT).show();

                SharedPreferences preferences = getBaseContext().getSharedPreferences("USER", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                String userTxt = "";
                byte[] encryptedPassword = new byte[0];
                try {
                    userTxt = user.getText().toString();
                    encryptedPassword = result.getCryptoObject().getCipher().doFinal(
                            password.getText().toString().getBytes(Charset.defaultCharset()));
                    Log.d(TAG, "Guardando Informacion: " +
                            userTxt + "\n"+
                            Arrays.toString(encryptedPassword));
                    editor.putString("USER", userTxt);
                    editor.putString("PASSWORD", Arrays.toString(encryptedPassword));
                    editor.commit();
                    doLogin(userTxt, Arrays.toString(encryptedPassword));

                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }
}