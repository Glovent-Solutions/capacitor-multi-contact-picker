package net.glocms.plugins.multicontactpicker;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wafflecopter.multicontactpicker.ContactResult;
import com.wafflecopter.multicontactpicker.MultiContactPicker;

@CapacitorPlugin(
  name = "MultiContactSelect",
  permissions = { @Permission(strings = { Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS }, alias = "contacts") }
)
public class MultiContactSelectPlugin extends Plugin {

    private MultiContactSelect implementation;

    @Override
    public void load() {
        implementation = new MultiContactSelect(getActivity());
    }

    private void requestContactsPermission(PluginCall call) {
        requestPermissionForAlias("contacts", call, "permissionCallback");
    }

    /**
     * Checks the the given permission is granted or not
     * @return Returns true if the permission is granted and false if it is denied.
     */
    private boolean isContactsPermissionGranted() {
        return getPermissionState("contacts") == PermissionState.GRANTED;
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            call.reject("Permission is required to access contacts.");
            return;
        }

        switch (call.getMethodName()) {
            case "getContact":
                getContact(call);
                break;
            case "getContacts":
                getContacts(call);
                break;
            case "createContact":
                createContact(call);
                break;
            case "deleteContact":
                deleteContact(call);
                break;
            case "pickContacts":
                pickContacts(call);
                break;
        }
    }

    @PluginMethod
    public void getContact(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            requestContactsPermission(call);
        } else {
            String contactId = call.getString("contactId");

            if (contactId == null) {
                call.reject("Parameter `contactId` not provided.");
                return;
            }

            GetContactsProjectionInput projectionInput = new GetContactsProjectionInput(call.getObject("projection"));

            ContactPayload contact = implementation.getContact(contactId, projectionInput);

            if (contact == null) {
                call.reject("Contact not found.");
                return;
            }

            JSObject result = new JSObject();
            result.put("contact", contact.getJSObject());
            call.resolve(result);
        }
    }

    @PluginMethod
    public void getContacts(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            requestContactsPermission(call);
        } else {
            HashMap<String, ContactPayload> contacts = implementation.getContacts(
                new GetContactsProjectionInput(call.getObject("projection"))
            );

            JSArray contactsJSArray = new JSArray();

            for (Map.Entry<String, ContactPayload> entry : contacts.entrySet()) {
                ContactPayload value = entry.getValue();
                contactsJSArray.put(value.getJSObject());
            }

            JSObject result = new JSObject();
            result.put("contacts", contactsJSArray);
            call.resolve(result);
        }
    }

    @PluginMethod
    public void createContact(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            requestContactsPermission(call);
        } else {
            String contactId = implementation.createContact(new CreateContactInput(call.getObject("contact")));

            if (contactId == null) {
                call.reject("Something went wrong.");
                return;
            }

            JSObject result = new JSObject();
            result.put("contactId", contactId);

            call.resolve(result);
        }
    }

    @PluginMethod
    public void deleteContact(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            requestContactsPermission(call);
        } else {
            String contactId = call.getString("contactId");

            if (contactId == null) {
                call.reject("Parameter `contactId` not provided.");
                return;
            }

            if (!implementation.deleteContact(contactId)) {
                call.reject("Something went wrong.");
                return;
            }

            call.resolve();
        }
    }

    @PluginMethod
    public void pickContacts(PluginCall call) {
        if (!isContactsPermissionGranted()) {
            requestContactsPermission(call);
        } else {
//            Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
          int CONTACT_PICKER_REQUEST = 991;


          Intent contactPickerIntent = new MultiContactPicker.Builder(getActivity()) //Activity/fragment context
            .hideScrollbar(false) //Optional - default: false
            .showTrack(true) //Optional - default: true
            .searchIconColor(Color.WHITE) //Optional - default: White
            .bubbleTextColor(Color.WHITE) //Optional - default: White
            .setTitleText("Select Contacts") //Optional - only use if required
            .setActivityAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
              android.R.anim.fade_in,
              android.R.anim.fade_out) //Optional - default: No animation overrides
            .showPickerForResult(CONTACT_PICKER_REQUEST);

          Log.i("MultiContactPicker", "########################## startActivityForResult ###########################");


          startActivityForResult(call, contactPickerIntent, "pickContactsResult");

        }
    }

    @ActivityCallback
    private void pickContactsResult(PluginCall call, ActivityResult activityResult) {
      Log.i("MultiContactPicker", "########################## pickContactsResult ###########################");
      Log.i("activityResult", activityResult.toString());

      int resultCode = activityResult.getResultCode();
      Log.i("resultCode", String.valueOf(resultCode));

      Intent data = activityResult.getData();
      Log.i("data", String.valueOf(data));

      int RESULT_OK = -1;
      int RESULT_CANCELED = 0;

      JSObject result = new JSObject();

      if(resultCode == RESULT_OK) {
        List<ContactResult> results = MultiContactPicker.obtainResult(data);
        JSArray contactsJSArray = new JSArray();
        for (int i = 0; i < results.size(); i++) {
          GetContactsProjectionInput projectionInput = new GetContactsProjectionInput(call.getObject("projection"));
          ContactPayload contact = implementation.getContact(results.get(i).getContactID(), projectionInput);
          contactsJSArray.put(contact.getJSObject());
        }
        result.put("message", "OK");
        result.put("contacts", contactsJSArray);
        call.resolve(result);
      } else if(resultCode == RESULT_CANCELED){
        result.put("message", "User closed the picker without selecting items.");
        call.resolve(result);
      }

//      if (call != null && activityResult.getResultCode() == Activity.RESULT_OK && activityResult.getData() != null) {
//            // This will return a URI for retrieving the contact, e.g.: "content://com.android.contacts/contacts/1234"
//            Uri uri = activityResult.getData().getData();
//            // Parse the contactId from this URI.
//            String contactId = MultiContactSelect.getIdFromUri(uri);
//
//            if (contactId == null) {
//                call.reject("Parameter `contactId` not returned from pick. Please raise an issue in GitHub if this problem persists.");
//                return;
//            }
//
//            GetContactsProjectionInput projectionInput = new GetContactsProjectionInput(call.getObject("projection"));
//
//            ContactPayload contact = implementation.getContact(contactId, projectionInput);
//
//            if (contact == null) {
//                call.reject("Contact not found.");
//                return;
//            }
//
//            JSObject result = new JSObject();
//            result.put("contact", contact.getJSObject());
//            call.resolve(result);
//        }
    }
}
