package com.kust.websocketvpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class VPNSettingsEditor extends AppCompatActivity {


    public static class I {
        //actions
        public static final String CREATE_NEW = "CREATE_NEW";
        public static final String EDIT = "EDIT";

        //params
        public static final String PROFILE_NAME = "PROFILE_NAME";

    }

    private static class B {
        public static final String UNSAVED_CHANGES = "C";
    }

    private static final int SELECT_CERT_CODE = 234;

    private EditText profileName;
    private EditText serverURL;
    private EditText dnsAddr;
    private EditText serverCert;
    private Toolbar toolbar;

    private boolean unsavedChanges = false;


    private void selectCertFile(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(intent, SELECT_CERT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != SELECT_CERT_CODE) return;
        if (data == null) return;
        Uri fUri = data.getData();
        if (fUri == null) return;
        try {
            InputStreamReader in = new InputStreamReader(getContentResolver().openInputStream(fUri));
            StringBuilder sb = new StringBuilder();
            final int bufferSize = 1024;
            final char[] buffer = new char[bufferSize];
            int charsRead;
            while ((charsRead = in.read(buffer, 0, buffer.length)) > 0) {
                sb.append(buffer, 0, charsRead);
            }
            serverCert.setText(sb.toString());
            Toast.makeText(this, "Certificate file read successfully", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Toast.makeText(this, "Selected file doesn't exist or cannot be opened", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show();
        }


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_settings_editor);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(this::onSaveClick);

        profileName = findViewById(R.id.profile_name);
        serverURL = findViewById(R.id.server_url);
        dnsAddr = findViewById(R.id.dns_addr);
        serverCert = findViewById(R.id.server_cert);

        Button selectCert = findViewById(R.id.choose_cert);
        selectCert.setOnClickListener(this::selectCertFile);

        Button clearCert = findViewById(R.id.clear_cert);
        clearCert.setOnClickListener(v->{
            serverCert.setText("");
        });


        if (savedInstanceState != null && savedInstanceState.getBoolean(B.UNSAVED_CHANGES)) {
            unsavedChanges = true;

            //load old stuff
            profileName.setText(savedInstanceState.getString(WsvService.S.SELECTED_PROFILE));
            serverURL.setText(savedInstanceState.getString(WsvService.S.SERVER_URL));
            dnsAddr.setText(savedInstanceState.getString(WsvService.S.DNS_ADDR));
            serverCert.setText(savedInstanceState.getString(WsvService.S.SERVER_CERT));

        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent st = getIntent();

        String action = st.getAction();
        if (action == null) {
            throw new IllegalArgumentException("Should not be started without an action");
        }

        if (action.equals(I.CREATE_NEW)) {
            profileName.setEnabled(true);
        } else if (action.equals(I.EDIT)) {
            profileName.setEnabled(false);

            //load profile stuff
            if (!unsavedChanges) {
                String profile = st.getStringExtra(I.PROFILE_NAME);
                profileName.setText(profile);

                SharedPreferences pref = getSharedPreferences(profile, MODE_PRIVATE);

                serverURL.setText(pref.getString(WsvService.S.SERVER_URL, "Invalid/corrupted profile"));
                dnsAddr.setText(pref.getString(WsvService.S.DNS_ADDR, "8.8.8.8"));
                serverCert.setText(pref.getString(WsvService.S.SERVER_CERT, ""));
            }
        } else {
            throw new IllegalArgumentException("Invalid action");
        }

        toolbar.setTitle(profileName.getText().toString());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(B.UNSAVED_CHANGES, unsavedChanges);

        if (unsavedChanges) {
            outState.putString(WsvService.S.SELECTED_PROFILE, profileName.getText().toString());
            outState.putString(WsvService.S.SERVER_URL, serverURL.getText().toString());
            outState.putString(WsvService.S.DNS_ADDR, dnsAddr.getText().toString());
            outState.putString(WsvService.S.SERVER_CERT, serverCert.getText().toString());
        }

        super.onSaveInstanceState(outState);
    }

    public void onSaveClick(View v) {
        //TODO validate

        String profile = profileName.getText().toString();
        SharedPreferences pref = getSharedPreferences(profile, MODE_PRIVATE);
        pref.edit()
                .putString(WsvService.S.SERVER_URL, serverURL.getText().toString())
                .putString(WsvService.S.DNS_ADDR, dnsAddr.getText().toString())
                .putString(WsvService.S.SERVER_CERT, serverCert.getText().toString())

                .apply();

        SharedPreferences glob = getSharedPreferences(WsvUI.S.GLOBAL_SETTINGS, MODE_PRIVATE);

        @NonNull Set<String> allProfiles = Objects.requireNonNull(glob.getStringSet(WsvUI.S.PROFILE_LIST, new HashSet<>()));
        allProfiles.add(profile);
        Log.i(TAG, "new profile list: " + allProfiles.toString());
        glob.edit().putStringSet(WsvUI.S.PROFILE_LIST, allProfiles).apply();

        unsavedChanges = false;

        finish();
    }

    private final static String TAG = "wsv/settings";
}