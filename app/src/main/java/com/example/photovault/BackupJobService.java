package com.example.photovault;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.FileInputStream;
import java.util.Collections;
import java.util.Calendar;

public class BackupJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 3 && hour < 5) {
            new Thread(() -> {
                try {
                    performBackup();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                jobFinished(params, false);
            }).start();
            return true;
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private void performBackup() throws Exception {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) return;

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());

        Drive googleDriveService = new Drive.Builder(
                new NetHttpTransport(),
                AndroidJsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("PhotoVault")
                .build();

        // Backup main nomedia folder
        java.io.File nomediaDir = new java.io.File(getFilesDir(), ".nomedia");
        if (nomediaDir.exists()) {
            java.io.File[] files = nomediaDir.listFiles();
            if (files != null) {
                for (java.io.File localFile : files) {
                    if (localFile.isFile()) {
                        File fileMetadata = new File();
                        fileMetadata.setName("pv_backup_" + localFile.getName());

                        com.google.api.client.http.FileContent mediaContent = 
                                new com.google.api.client.http.FileContent("application/octet-stream", localFile);
                        googleDriveService.files().create(fileMetadata, mediaContent)
                                .setFields("id")
                                .execute();
                    }
                }
            }
        }
    }
}
