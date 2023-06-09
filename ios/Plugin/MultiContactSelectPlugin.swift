import Foundation
import Capacitor
import Contacts
import ContactsUI
import KNContactsPicker

enum CallingMethod {
    case GetContact
    case GetContacts
    case CreateContact
    case DeleteContact
    case pickContacts
}

@objc(MultiContactSelectPlugin)
public class MultiContactSelectPlugin: CAPPlugin, CNContactPickerDelegate, KNContactPickingDelegate {
    public func contactPicker(didFailPicking error: Error) {
        print(error)
    }

    public func contactPicker(didCancel error: Error) {
        print(error)
    }

    public func contactPicker(didSelect contact: CNContact) {
        print(contact)
    }

    public func contactPicker(didSelect contacts: [CNContact]) {
        print(contacts)

        let call = self.bridge?.savedCall(withID: self.pickContactsCallbackId ?? "")

        guard let call = call else {
            return
        }

        var contactsJSArray: JSArray = JSArray()

        for contact in contacts {
            let jsonContact = ContactPayload(contact.identifier);
            jsonContact.fillData(contact)
            contactsJSArray.append(jsonContact.getJSObject())
        }

        call.resolve([
            "message":"OK",
            "contacts": contactsJSArray
        ])
    }

    private let implementation = MultiContactSelect()

    private var callingMethod: CallingMethod?

    private var pickContactsCallbackId: String?

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let permissionState: String

        switch CNContactStore.authorizationStatus(for: .contacts) {
        case .notDetermined:
            permissionState = "prompt"
        case .restricted, .denied:
            permissionState = "denied"
        case .authorized:
            permissionState = "granted"
        @unknown default:
            permissionState = "prompt"
        }

        call.resolve([
            "contacts": permissionState
        ])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        CNContactStore().requestAccess(for: .contacts) { [weak self] _, _  in
            self?.checkPermissions(call)
        }
    }

    private func requestContactsPermission(_ call: CAPPluginCall, _ callingMethod: CallingMethod) {
        self.callingMethod = callingMethod
        if isContactsPermissionGranted() {
            permissionCallback(call)
        } else {
            CNContactStore().requestAccess(for: .contacts) { [weak self] _, _  in
                self?.permissionCallback(call)
            }
        }
    }

    private func isContactsPermissionGranted() -> Bool {
        switch CNContactStore.authorizationStatus(for: .contacts) {
        case .notDetermined, .restricted, .denied:
            return false
        case .authorized:
            return true
        @unknown default:
            return false
        }
    }

    private func permissionCallback(_ call: CAPPluginCall) {
        let method = self.callingMethod

        self.callingMethod = nil

        if !isContactsPermissionGranted() {
            call.reject("Permission is required to access contacts.")
            return
        }

        switch method {
        case .GetContact:
            getContact(call)
        case .GetContacts:
            getContacts(call)
        case .CreateContact:
            createContact(call)
        case .DeleteContact:
            deleteContact(call)
        case .pickContacts:
            pickContacts(call)
        default:
            // No method was being called,
            // so nothing has to be done here.
            break
        }
    }

    @objc func getContact(_ call: CAPPluginCall) {
        if !isContactsPermissionGranted() {
            requestContactsPermission(call, CallingMethod.GetContact)
        } else {
            let contactId = call.getString("contactId")

            guard let contactId = contactId else {
                call.reject("Parameter `contactId` not provided.")
                return
            }

            let projectionInput = GetContactsProjectionInput(call.getObject("projection") ?? JSObject())

            let contact = implementation.getContact(contactId, projectionInput)

            guard let contact = contact else {
                call.reject("Contact not found.")
                return
            }

            call.resolve([
                "contact": contact.getJSObject()
            ])
        }
    }

    @objc func getContacts(_ call: CAPPluginCall) {
        if !isContactsPermissionGranted() {
            requestContactsPermission(call, CallingMethod.GetContacts)
        } else {
            let projectionInput = GetContactsProjectionInput(call.getObject("projection") ?? JSObject())

            let contacts = implementation.getContacts(projectionInput)

            var contactsJSArray: JSArray = JSArray()

            for contact in contacts {
                contactsJSArray.append(contact.getJSObject())
            }

            call.resolve([
                "contacts": contactsJSArray
            ])
        }
    }

    @objc func createContact(_ call: CAPPluginCall) {
        if !isContactsPermissionGranted() {
            requestContactsPermission(call, CallingMethod.CreateContact)
        } else {
            let contactInput = CreateContactInput.init(call.getObject("contact", JSObject()))

            let contactId = implementation.createContact(contactInput)

            guard let contactId = contactId else {
                call.reject("Something went wrong.")
                return
            }

            call.resolve([
                "contactId": contactId
            ])
        }
    }

    @objc func deleteContact(_ call: CAPPluginCall) {
        if !isContactsPermissionGranted() {
            requestContactsPermission(call, CallingMethod.DeleteContact)
        } else {
            let contactId = call.getString("contactId")

            guard let contactId = contactId else {
                call.reject("Parameter `contactId` not provided.")
                return
            }

            if !implementation.deleteContact(contactId) {
                call.reject("Something went wrong.")
                return
            }

            call.resolve()
        }
    }

    @objc func pickContacts(_ call: CAPPluginCall) {
        if !isContactsPermissionGranted() {
            requestContactsPermission(call, CallingMethod.pickContacts)
        } else {
            DispatchQueue.main.async {
                // Save the call and its callback id
                self.bridge?.saveCall(call)
                self.pickContactsCallbackId = call.callbackId


                var settings = KNPickerSettings()
                settings.pickerTitle = "Select Contacts"
                settings.selectionMode = .multiple

                let controller = KNContactsPicker(delegate: self, settings: settings)

                self.bridge?.viewController?.present(controller, animated: true, completion: nil)

                // Initialize the contact picker
//                let contactPicker = CNContactPickerViewController()
                // Mark current class as the delegate class,
                // this will make the callback `contactPicker` actually work.
//                contactPicker.delegate = self
                // Present (open) the native contact picker.
//                self.bridge?.viewController?.present(contactPicker, animated: true)
            }
        }
    }

    public func contactPicker(_ picker: CNContactPickerViewController, didSelect selectedContact: CNContact) {
        let call = self.bridge?.savedCall(withID: self.pickContactsCallbackId ?? "")

        guard let call = call else {
            return
        }

        let contact = ContactPayload(selectedContact.identifier)

        contact.fillData(selectedContact)

        call.resolve([
            "contact": contact.getJSObject()
        ])

        self.bridge?.releaseCall(call)
    }
}
