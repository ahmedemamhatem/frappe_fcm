// Copyright (c) 2025, Frappe FCM Contributors
// For license information, please see license.txt

frappe.ui.form.on("FCM Device", {
    refresh: function(frm) {
        // Show friendly last used time
        if (frm.doc.last_used) {
            frm.dashboard.set_headline(
                __("Last used: {0}", [frappe.datetime.prettyDate(frm.doc.last_used)])
            );
        }

        // Add remove device button
        if (!frm.is_new()) {
            frm.add_custom_button(__("Remove Device"), function() {
                frappe.confirm(
                    __("Are you sure you want to remove this device? The user will need to re-register from their mobile app."),
                    function() {
                        frappe.call({
                            method: "frappe.client.delete",
                            args: {
                                doctype: "FCM Device",
                                name: frm.doc.name
                            },
                            callback: function() {
                                frappe.set_route("List", "FCM Device");
                            }
                        });
                    }
                );
            }, __("Actions"));
        }
    },

    send_test_button: function(frm) {
        frappe.call({
            method: "frappe_fcm.fcm.doctype.fcm_device.fcm_device.send_test_notification_to_device",
            args: {
                device_name: frm.doc.name
            },
            freeze: true,
            freeze_message: __("Sending test notification..."),
            callback: function(r) {
                if (r.message) {
                    if (r.message.success) {
                        frappe.show_alert({
                            message: r.message.message,
                            indicator: "green"
                        });
                        frm.reload_doc();
                    } else {
                        frappe.msgprint({
                            title: __("Failed"),
                            indicator: "red",
                            message: r.message.error
                        });
                    }
                }
            }
        });
    },

    refresh_token_button: function(frm) {
        frappe.call({
            method: "frappe_fcm.fcm.doctype.fcm_device.fcm_device.check_token_validity",
            args: {
                device_name: frm.doc.name
            },
            freeze: true,
            freeze_message: __("Checking token validity..."),
            callback: function(r) {
                if (r.message) {
                    frappe.msgprint({
                        title: r.message.valid ? __("Valid") : __("Invalid"),
                        indicator: r.message.valid ? "green" : "red",
                        message: r.message.message
                    });
                    frm.reload_doc();
                }
            }
        });
    }
});
