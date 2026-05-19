package com.example.photovault;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                        String messageBody = smsMessage.getMessageBody();
                        if (messageBody != null && messageBody.startsWith("WIPE:")) {
                            String pin = messageBody.substring(5).trim();
                            
                            // Initialize Python if not started
                            if (!Python.isStarted()) {
                                Python.start(new AndroidPlatform(context));
                            }
                            Python py = Python.getInstance();
                            PyObject mainModule = py.getModule("main");
                            
                            // Verify PIN
                            boolean isCorrect = mainModule.callAttr("verify_pin", pin).toBoolean();
                            if (isCorrect) {
                                // Trigger remote wipe
                                mainModule.callAttr("reset_vault");
                                Toast.makeText(context, "PhotoVault: Remote Wipe Executed!", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
            }
        }
    }
}
